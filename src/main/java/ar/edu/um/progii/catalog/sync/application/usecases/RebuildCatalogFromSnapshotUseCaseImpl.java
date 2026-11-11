package ar.edu.um.progii.catalog.sync.application.usecases;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogCredentialsRejectedException;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InvalidCatalogSnapshotException;
import ar.edu.um.progii.catalog.sync.application.exception.ReplicaWriteFailedException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.RebuildCatalogFromSnapshotUseCase;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogSnapshotSource;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncLock;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncStateRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// CU-2. El snapshot se baja fuera de la escritura, que es corta y guarda junto la copia, el estado y la ejecución. No
// llama a la incremental: la incremental lo llama a él y sigue su ciclo después (decisión 24)
@Component
@RequiredArgsConstructor
public class RebuildCatalogFromSnapshotUseCaseImpl implements RebuildCatalogFromSnapshotUseCase {

	private final SyncLock syncLock;
	private final CatalogSnapshotSource catalogSnapshotSource;
	private final SyncStateRepository syncStateRepository;
	private final CatalogReplicaWriter catalogReplicaWriter;

	@Override
	public void rebuildCatalogFromSnapshot(SyncTrigger trigger) {

		// Llamado desde la incremental, que ya tiene el lock, lo vuelve a obtener: el lock admite volver a tomarlo
		if (!syncLock.tryAcquire()) {
			throw new SyncAlreadyRunningException("Another synchronization is writing the catalog");
		}
		try {
			rebuild(trigger);
		} finally {
			syncLock.release();
		}

	}

	// Si algo falla, registra la falla y relanza la excepción, para que quien llamó sepa por qué
	private void rebuild(SyncTrigger trigger) {

		Instant startedAt = Instant.now();
		SyncState currentState = syncStateRepository.findCurrent();
		try {
			CatalogSnapshot catalogSnapshot = catalogSnapshotSource.readSnapshot();
			Instant appliedAt = Instant.now();
			SyncRun successfulRun = SyncRun.builder()
					.triggerSource(trigger)
					.mode(SyncMode.FULL)
					.fromVersion(currentState.getAppliedVersion())
					.toVersion(catalogSnapshot.getSnapshotVersion())
					.runResult(SyncRunResult.SUCCESS)
					.entitiesApplied(countEntities(catalogSnapshot))
					.startedAt(startedAt)
					.finishedAt(appliedAt)
					.build();
			catalogReplicaWriter.replaceAll(catalogSnapshot,
					currentState.afterSnapshotApplied(catalogSnapshot.getSnapshotVersion(), appliedAt), successfulRun);
		} catch (CatalogSourceUnavailableException unavailableException) {
			recordFailure(trigger, currentState, startedAt, SyncErrorCode.CATALOG_SOURCE_UNAVAILABLE,
					unavailableException);
			throw unavailableException;
		} catch (CatalogCredentialsRejectedException credentialsException) {
			recordFailure(trigger, currentState, startedAt, SyncErrorCode.CATALOG_CREDENTIALS_REJECTED,
					credentialsException);
			throw credentialsException;
		} catch (InvalidCatalogSnapshotException invalidSnapshotException) {
			recordFailure(trigger, currentState, startedAt, SyncErrorCode.INVALID_CATALOG_SNAPSHOT,
					invalidSnapshotException);
			throw invalidSnapshotException;
		} catch (ReplicaWriteFailedException writeException) {
			recordFailure(trigger, currentState, startedAt, SyncErrorCode.REPLICA_WRITE_FAILED, writeException);
			throw writeException;
		}

	}

	private void recordFailure(SyncTrigger trigger, SyncState currentState, Instant startedAt, SyncErrorCode errorCode,
			RuntimeException failureException) {

		Instant failedAt = Instant.now();
		SyncRun failedRun = SyncRun.builder()
				.triggerSource(trigger)
				.mode(SyncMode.FULL)
				.fromVersion(currentState.getAppliedVersion())
				.runResult(SyncRunResult.FAILED)
				.startedAt(startedAt)
				.finishedAt(failedAt)
				.errorCode(errorCode.name())
				.errorMessage(failureException.getMessage())
				.build();
		catalogReplicaWriter.recordRun(
				currentState.afterFailure(errorCode, failureException.getMessage(), failedAt), failedRun);

	}

	private static int countEntities(CatalogSnapshot catalogSnapshot) {

		return catalogSnapshot.getProfessionalCategories().size() + catalogSnapshot.getProfessionals().size()
				+ catalogSnapshot.getWeeklySchedules().size();

	}
}
