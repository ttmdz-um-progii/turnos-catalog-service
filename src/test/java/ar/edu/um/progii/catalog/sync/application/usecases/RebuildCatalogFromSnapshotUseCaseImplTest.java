package ar.edu.um.progii.catalog.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogCredentialsRejectedException;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InvalidCatalogSnapshotException;
import ar.edu.um.progii.catalog.sync.application.exception.ReplicaWriteFailedException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.domain.model.SyncOrigin;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogSnapshotSource;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncLock;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncStateRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class RebuildCatalogFromSnapshotUseCaseImplTest {

	// Lock que se puede dejar ocupado y que cuenta cuántas veces se tomó y se liberó
	static class CountingSyncLock implements SyncLock {

		private boolean takenByAnotherSync;
		private int acquisitions;
		private int releases;

		public boolean tryAcquire() {

			if (takenByAnotherSync) {
				return false;
			}
			acquisitions++;
			return true;

		}

		public void release() {

			releases++;

		}
	}

	// Devuelve el snapshot cargado o lanza la falla cargada, y cuenta los pedidos
	static class StubCatalogSnapshotSource implements CatalogSnapshotSource {

		private CatalogSnapshot catalogSnapshotToReturn;
		private RuntimeException failureToThrow;
		private int reads;

		public CatalogSnapshot readSnapshot() {

			reads++;
			if (failureToThrow != null) {
				throw failureToThrow;
			}
			return catalogSnapshotToReturn;

		}
	}

	static class InMemorySyncStateRepository implements SyncStateRepository {

		private SyncState currentState;

		public SyncState findCurrent() {

			return currentState;

		}
	}

	// Guarda lo último que recibió cada método; replaceAll puede fallar como lo haría la base
	static class RecordingCatalogReplicaWriter implements CatalogReplicaWriter {

		private ReplicaWriteFailedException replaceFailureToThrow;
		private CatalogSnapshot replacedSnapshot;
		private SyncState stateAfterSnapshot;
		private SyncRun successfulRun;
		private SyncState stateAfterFailure;
		private SyncRun failedRun;

		public void replaceAll(CatalogSnapshot catalogSnapshot, SyncState stateAfterSnapshot, SyncRun successfulRun) {

			if (replaceFailureToThrow != null) {
				throw replaceFailureToThrow;
			}
			this.replacedSnapshot = catalogSnapshot;
			this.stateAfterSnapshot = stateAfterSnapshot;
			this.successfulRun = successfulRun;

		}

		// La reconstrucción nunca aplica cambios sueltos
		public void applyChanges(CurrentCatalogEntities currentCatalogEntities, SyncState stateAfterChanges,
				SyncRun successfulRun) {

			throw new UnsupportedOperationException("The rebuild never applies changes");

		}

		// La reconstrucción solo registra así sus fallas
		public void recordRun(SyncState stateAfterRun, SyncRun recordedRun) {

			this.stateAfterFailure = stateAfterRun;
			this.failedRun = recordedRun;

		}
	}

	private static final SyncState NEVER_SYNCED_STATE = new SyncState(null, null, SyncStatus.EMPTY, null, null, null,
			0, null, null);
	private static final SyncState STATE_WITH_VERSION_5 = new SyncState(5L, 5L, SyncStatus.IN_SYNC, null, null, null,
			0, null, null);
	private static final CatalogSnapshot SNAPSHOT_VERSION_7 = new CatalogSnapshot(7L,
			List.of(new ProfessionalCategory(1L, "Clinica medica", null, true)),
			List.of(new Professional(15L, 1L, "Ana", "Gomez", true)), List.of(new WeeklySchedule(21L, 15L,
					DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true)));

	private final CountingSyncLock syncLock = new CountingSyncLock();
	private final StubCatalogSnapshotSource catalogSnapshotSource = new StubCatalogSnapshotSource();
	private final InMemorySyncStateRepository syncStateRepository = new InMemorySyncStateRepository();
	private final RecordingCatalogReplicaWriter catalogReplicaWriter = new RecordingCatalogReplicaWriter();
	private final RebuildCatalogFromSnapshotUseCaseImpl rebuildCatalogFromSnapshotUseCase = new RebuildCatalogFromSnapshotUseCaseImpl(
			syncLock, catalogSnapshotSource, syncStateRepository, catalogReplicaWriter);

	@Test
	void replacesTheCatalogWithTheSnapshot() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE;
		catalogSnapshotSource.catalogSnapshotToReturn = SNAPSHOT_VERSION_7;

		rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.notification("catalog-event-7"));

		assertThat(catalogReplicaWriter.replacedSnapshot).isSameAs(SNAPSHOT_VERSION_7);
		assertThat(catalogReplicaWriter.stateAfterSnapshot.getAppliedVersion()).isEqualTo(7L);
		assertThat(catalogReplicaWriter.stateAfterSnapshot.getStatus()).isEqualTo(SyncStatus.IN_SYNC);
		SyncRun successfulRun = catalogReplicaWriter.successfulRun;
		assertThat(successfulRun.getTriggerSource()).isEqualTo(SyncTrigger.NOTIFICATION);
		assertThat(successfulRun.getEventId()).isEqualTo("catalog-event-7");
		assertThat(successfulRun.getMode()).isEqualTo(SyncMode.FULL);
		assertThat(successfulRun.getRunResult()).isEqualTo(SyncRunResult.SUCCESS);
		assertThat(successfulRun.getFromVersion()).isNull();
		assertThat(successfulRun.getToVersion()).isEqualTo(7L);
		assertThat(successfulRun.getEntitiesApplied()).isEqualTo(3);
		assertThat(catalogReplicaWriter.failedRun).isNull();
		assertThat(syncLock.releases).isEqualTo(syncLock.acquisitions).isEqualTo(1);

	}

	@Test
	void goesToErrorWhenTheCredentialsAreRejected() {

		syncStateRepository.currentState = STATE_WITH_VERSION_5;
		CatalogCredentialsRejectedException credentialsException = new CatalogCredentialsRejectedException(
				"/api/synchronization/snapshot answered 401 with code null");
		catalogSnapshotSource.failureToThrow = credentialsException;

		assertThatThrownBy(() -> rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.manual()))
				.isSameAs(credentialsException);

		assertThat(catalogReplicaWriter.replacedSnapshot).isNull();
		assertThat(catalogReplicaWriter.stateAfterFailure.getStatus()).isEqualTo(SyncStatus.ERROR);
		assertThat(catalogReplicaWriter.stateAfterFailure.getAppliedVersion()).isEqualTo(5L);
		assertThat(catalogReplicaWriter.failedRun.getRunResult()).isEqualTo(SyncRunResult.FAILED);
		assertThat(catalogReplicaWriter.failedRun.getMode()).isEqualTo(SyncMode.FULL);
		assertThat(catalogReplicaWriter.failedRun.getFromVersion()).isEqualTo(5L);
		assertThat(catalogReplicaWriter.failedRun.getToVersion()).isNull();
		assertThat(catalogReplicaWriter.failedRun.getErrorCode()).isEqualTo("CATALOG_CREDENTIALS_REJECTED");
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void staysEmptyWhenTheSourceIsUnavailable() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE;
		catalogSnapshotSource.failureToThrow = new CatalogSourceUnavailableException("Could not reach the catalog");

		assertThatThrownBy(() -> rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.startup()))
				.isInstanceOf(CatalogSourceUnavailableException.class);

		assertThat(catalogReplicaWriter.stateAfterFailure.getStatus()).isEqualTo(SyncStatus.EMPTY);
		assertThat(catalogReplicaWriter.stateAfterFailure.getConsecutiveFailures()).isEqualTo(1);
		assertThat(catalogReplicaWriter.failedRun.getErrorCode()).isEqualTo("CATALOG_SOURCE_UNAVAILABLE");
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void recordsWhyTheSnapshotIsInvalid() {

		syncStateRepository.currentState = STATE_WITH_VERSION_5;
		catalogSnapshotSource.failureToThrow = new InvalidCatalogSnapshotException(
				"Invalid snapshot: Professional 15 references the missing category 3");

		assertThatThrownBy(() -> rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.manual()))
				.isInstanceOf(InvalidCatalogSnapshotException.class);

		assertThat(catalogReplicaWriter.stateAfterFailure.getStatus()).isEqualTo(SyncStatus.DEGRADED);
		assertThat(catalogReplicaWriter.failedRun.getErrorCode()).isEqualTo("INVALID_CATALOG_SNAPSHOT");
		assertThat(catalogReplicaWriter.failedRun.getErrorMessage())
				.isEqualTo("Invalid snapshot: Professional 15 references the missing category 3");

	}

	@Test
	void recordsAFailedWrite() {

		syncStateRepository.currentState = STATE_WITH_VERSION_5;
		catalogSnapshotSource.catalogSnapshotToReturn = SNAPSHOT_VERSION_7;
		catalogReplicaWriter.replaceFailureToThrow = new ReplicaWriteFailedException("Could not replace the catalog",
				new RuntimeException("connection lost"));

		assertThatThrownBy(() -> rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.manual()))
				.isInstanceOf(ReplicaWriteFailedException.class);

		assertThat(catalogReplicaWriter.stateAfterFailure.getAppliedVersion()).isEqualTo(5L);
		assertThat(catalogReplicaWriter.failedRun.getErrorCode()).isEqualTo("REPLICA_WRITE_FAILED");
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void doesNothingWhileAnotherSyncIsRunning() {

		syncLock.takenByAnotherSync = true;

		assertThatThrownBy(() -> rebuildCatalogFromSnapshotUseCase.rebuildCatalogFromSnapshot(SyncOrigin.scheduled()))
				.isInstanceOf(SyncAlreadyRunningException.class);

		assertThat(catalogSnapshotSource.reads).isZero();
		assertThat(catalogReplicaWriter.failedRun).isNull();
		// No libera un lock que no tomó
		assertThat(syncLock.releases).isZero();

	}
}
