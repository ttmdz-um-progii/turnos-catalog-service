package ar.edu.um.progii.catalog.sync.application.usecases;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InconsistentChangesException;
import ar.edu.um.progii.catalog.sync.application.exception.ReplicaWriteFailedException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncFailureException;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.SyncAction;
import ar.edu.um.progii.catalog.sync.domain.model.SyncDecider;
import ar.edu.um.progii.catalog.sync.domain.model.SyncDecision;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncOrigin;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import ar.edu.um.progii.catalog.sync.domain.ports.in.RebuildCatalogFromSnapshotUseCase;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogChangeSource;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncLock;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncRunRepository;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncStateRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// CU-1. Cada pasada lee lo que publicó la cátedra, decide con SyncDecider y aplica los cambios de localVersion + 1 a
// currentVersion en una sola escritura, o reconstruye desde el snapshot si la cadena no se puede verificar. Después
// vuelve a mirar: si la cátedra publicó algo más mientras tanto, hace otra pasada (decisión 24). Las lecturas de la
// cátedra quedan fuera de la escritura. Un aviso que ya se procesó solo se registra como NOOP (decisión 12)
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncCatalogUseCaseImpl implements SyncCatalogUseCase {

	// Tope de pasadas por ejecución: si la cátedra publica sin parar, la próxima ejecución sigue desde donde quedó
	private static final int MAX_PASSES = 5;

	private final SyncLock syncLock;
	private final SyncStateRepository syncStateRepository;
	private final SyncRunRepository syncRunRepository;
	private final CatalogChangeSource catalogChangeSource;
	private final CatalogReplicaWriter catalogReplicaWriter;
	private final RebuildCatalogFromSnapshotUseCase rebuildCatalogFromSnapshotUseCase;

	// Lo que pasó en una pasada. FINISHED corta la ejecución: no había nada que aplicar, o haría falta un segundo
	// snapshot
	private enum PassOutcome {
		APPLIED_CHANGES,
		APPLIED_SNAPSHOT,
		FINISHED
	}

	// Lo que encontró la lectura de una pasada: qué hacer, hasta qué versión y, si es incremental, las entidades
	private record PendingChanges(SyncDecision syncDecision, long currentVersion,
			CurrentCatalogEntities currentCatalogEntities) {
	}

	@Override
	public void syncCatalog(SyncOrigin syncOrigin) {

		if (!syncLock.tryAcquire()) {
			throw new SyncAlreadyRunningException("Another synchronization is writing the catalog");
		}
		try {
			if (isRepeatedNotification(syncOrigin)) {
				recordRepeatedNotification(syncOrigin);
				return;
			}
			boolean snapshotApplied = false;
			for (int passNumber = 1; passNumber <= MAX_PASSES; passNumber++) {
				PassOutcome passOutcome = runPass(syncOrigin, passNumber == 1, snapshotApplied);
				if (passOutcome == PassOutcome.FINISHED) {
					return;
				}
				if (passOutcome == PassOutcome.APPLIED_SNAPSHOT) {
					snapshotApplied = true;
				}
			}
			log.warn("Stopped after {} passes: the catalog kept publishing new versions", MAX_PASSES);
		} finally {
			syncLock.release();
		}

	}

	private PassOutcome runPass(SyncOrigin syncOrigin, boolean firstPass, boolean snapshotApplied) {

		Instant startedAt = Instant.now();
		SyncState currentState = syncStateRepository.findCurrent();
		// Sin copia no hay desde dónde avanzar: no hace falta preguntarle nada a la cátedra
		if (currentState.getAppliedVersion() == null) {
			return rebuildFromSnapshot(syncOrigin);
		}
		long localVersion = currentState.getAppliedVersion();

		PendingChanges pendingChanges;
		try {
			pendingChanges = readPendingChanges(localVersion);
		} catch (CatalogSourceUnavailableException unavailableException) {
			recordFailure(syncOrigin, currentState, startedAt, unavailableException);
			throw unavailableException;
		}

		return switch (pendingChanges.syncDecision().getAction()) {
			case NOOP -> {
				// Una pasada posterior que no encuentra nada solo confirma lo que ya se registró
				if (firstPass) {
					Instant checkedAt = Instant.now();
					recordNoop(syncOrigin, currentState,
							currentState.afterNoop(pendingChanges.currentVersion(), checkedAt), startedAt, checkedAt);
				}
				yield PassOutcome.FINISHED;
			}
			case SNAPSHOT -> rebuildOnceFromSnapshot(syncOrigin, currentState, startedAt,
					pendingChanges.currentVersion(), snapshotApplied);
			case INCREMENTAL -> applyChanges(syncOrigin, currentState, startedAt, pendingChanges, snapshotApplied);
		};

	}

	// Las tres etapas de SyncDecider, cada una con su lectura: una etapa que decide corta las lecturas siguientes
	private PendingChanges readPendingChanges(long localVersion) {

		PublishedVersions publishedVersions = catalogChangeSource.readVersions();
		long currentVersion = publishedVersions.getCurrentVersion();
		Optional<SyncDecision> decisionByVersions = SyncDecider.decideByVersions(localVersion, currentVersion,
				publishedVersions.getOldestVersion());
		if (decisionByVersions.isPresent()) {
			return new PendingChanges(decisionByVersions.get(), currentVersion, null);
		}

		Map<Long, VersionChanges> changesByVersion = catalogChangeSource.readChanges(localVersion + 1, currentVersion);
		SyncDecision decisionByChain = SyncDecider.decideByChain(localVersion, currentVersion, changesByVersion);
		if (decisionByChain.getAction() == SyncAction.SNAPSHOT) {
			return new PendingChanges(decisionByChain, currentVersion, null);
		}

		CurrentCatalogEntities currentCatalogEntities = catalogChangeSource
				.readCurrentCatalog(decisionByChain.getIdsToApply());
		SyncDecision decisionByMissingIds = SyncDecider.decideByMissingIds(decisionByChain,
				currentCatalogEntities.getIdsMissingFromCurrentCatalog());
		return new PendingChanges(decisionByMissingIds, currentVersion, currentCatalogEntities);

	}

	private PassOutcome applyChanges(SyncOrigin syncOrigin, SyncState currentState, Instant startedAt,
			PendingChanges pendingChanges, boolean snapshotApplied) {

		CurrentCatalogEntities currentCatalogEntities = pendingChanges.currentCatalogEntities();
		Instant appliedAt = Instant.now();
		SyncRun successfulRun = SyncRun.builder()
				.triggerSource(syncOrigin.getTrigger())
				.eventId(syncOrigin.getEventId())
				.mode(SyncMode.INCREMENTAL)
				.fromVersion(currentState.getAppliedVersion())
				.toVersion(pendingChanges.currentVersion())
				.runResult(SyncRunResult.SUCCESS)
				.entitiesApplied(currentCatalogEntities.getProfessionalCategories().size()
						+ currentCatalogEntities.getProfessionals().size()
						+ currentCatalogEntities.getWeeklySchedules().size())
				.startedAt(startedAt)
				.finishedAt(appliedAt)
				.build();
		try {
			catalogReplicaWriter.applyChanges(currentCatalogEntities,
					currentState.afterChangesApplied(pendingChanges.currentVersion(), appliedAt), successfulRun);
			return PassOutcome.APPLIED_CHANGES;
		} catch (InconsistentChangesException inconsistentChangesException) {
			// No es una falla pasajera: la copia no se puede verificar y se vuelve al snapshot
			log.warn("{}; rebuilding from the snapshot", inconsistentChangesException.getMessage());
			return rebuildOnceFromSnapshot(syncOrigin, currentState, startedAt, pendingChanges.currentVersion(),
					snapshotApplied);
		} catch (ReplicaWriteFailedException writeException) {
			recordFailure(syncOrigin, currentState, startedAt, writeException);
			throw writeException;
		}

	}

	// Un segundo snapshot en la misma ejecución no arreglaría nada, porque la cátedra sigue publicando lo mismo: se
	// registra que la copia quedó atrasada y la próxima ejecución vuelve a intentar
	private PassOutcome rebuildOnceFromSnapshot(SyncOrigin syncOrigin, SyncState currentState, Instant startedAt,
			long currentVersion, boolean snapshotApplied) {

		if (snapshotApplied) {
			recordSnapshotBehindCatalog(syncOrigin, currentState, startedAt, currentVersion);
			return PassOutcome.FINISHED;
		}
		return rebuildFromSnapshot(syncOrigin);

	}

	// La reconstrucción registra su propio resultado, también si falla: su excepción solo se deja pasar
	private PassOutcome rebuildFromSnapshot(SyncOrigin syncOrigin) {

		rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(syncOrigin);
		return PassOutcome.APPLIED_SNAPSHOT;

	}

	// Se pregunta con el lock tomado: así un aviso que se está procesando ya terminó de registrarse
	private boolean isRepeatedNotification(SyncOrigin syncOrigin) {

		return syncOrigin.getEventId() != null && syncRunRepository.existsCompletedRunForEvent(syncOrigin.getEventId());

	}

	// La entrega de la cátedra es al menos una vez (anexo §18.1): el aviso repetido no lee la cátedra ni cambia el
	// estado, solo deja constancia de que llegó
	private void recordRepeatedNotification(SyncOrigin syncOrigin) {

		Instant checkedAt = Instant.now();
		SyncState currentState = syncStateRepository.findCurrent();
		recordNoop(syncOrigin, currentState, currentState, checkedAt, checkedAt);
		log.info("Notification {} was already processed; recorded as a no-op", syncOrigin.getEventId());

	}

	// La copia no cambia: la ejecución va de su versión a la misma
	private void recordNoop(SyncOrigin syncOrigin, SyncState currentState, SyncState stateAfterRun, Instant startedAt,
			Instant checkedAt) {

		SyncRun noopRun = SyncRun.builder()
				.triggerSource(syncOrigin.getTrigger())
				.eventId(syncOrigin.getEventId())
				.mode(SyncMode.INCREMENTAL)
				.fromVersion(currentState.getAppliedVersion())
				.toVersion(currentState.getAppliedVersion())
				.runResult(SyncRunResult.NOOP)
				.startedAt(startedAt)
				.finishedAt(checkedAt)
				.build();
		catalogReplicaWriter.recordRun(stateAfterRun, noopRun);

	}

	private void recordSnapshotBehindCatalog(SyncOrigin syncOrigin, SyncState currentState, Instant startedAt,
			long currentVersion) {

		Instant checkedAt = Instant.now();
		SyncState stateBehindCatalog = currentState.afterSnapshotBehindCatalog(currentVersion, checkedAt);
		SyncRun failedRun = SyncRun.builder()
				.triggerSource(syncOrigin.getTrigger())
				.eventId(syncOrigin.getEventId())
				.mode(SyncMode.INCREMENTAL)
				.fromVersion(currentState.getAppliedVersion())
				.runResult(SyncRunResult.FAILED)
				.startedAt(startedAt)
				.finishedAt(checkedAt)
				.errorCode(stateBehindCatalog.getLastErrorCode())
				.errorMessage(stateBehindCatalog.getLastErrorMessage())
				.build();
		catalogReplicaWriter.recordRun(stateBehindCatalog, failedRun);
		log.warn("{}; stopping until the next synchronization", stateBehindCatalog.getLastErrorMessage());

	}

	private void recordFailure(SyncOrigin syncOrigin, SyncState currentState, Instant startedAt,
			SyncFailureException syncFailureException) {

		Instant failedAt = Instant.now();
		SyncRun failedRun = SyncRun.builder()
				.triggerSource(syncOrigin.getTrigger())
				.eventId(syncOrigin.getEventId())
				.mode(SyncMode.INCREMENTAL)
				.fromVersion(currentState.getAppliedVersion())
				.runResult(SyncRunResult.FAILED)
				.startedAt(startedAt)
				.finishedAt(failedAt)
				.errorCode(syncFailureException.getErrorCode().name())
				.errorMessage(syncFailureException.getMessage())
				.build();
		catalogReplicaWriter.recordRun(
				currentState.afterFailure(syncFailureException.getErrorCode(), syncFailureException.getMessage(),
						failedAt),
				failedRun);

	}
}
