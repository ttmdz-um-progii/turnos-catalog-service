package ar.edu.um.progii.catalog.sync.application.usecases;

import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InconsistentChangesException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import ar.edu.um.progii.catalog.sync.domain.ports.in.RebuildCatalogFromSnapshotUseCase;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogChangeSource;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncLock;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SyncCatalogUseCaseImplTest {

	// Lock que se puede dejar ocupado y que cuenta cuántas veces se liberó
	static class CountingSyncLock implements SyncLock {

		private boolean takenByAnotherSync;
		private int releases;

		public boolean tryAcquire() {

			return !takenByAnotherSync;

		}

		public boolean tryAcquire(Duration maxWait) {

			return tryAcquire();

		}

		public void release() {

			releases++;

		}
	}

	static class InMemorySyncStateRepository implements SyncStateRepository {

		private SyncState currentState;

		public SyncState findCurrent() {

			return currentState;

		}
	}

	// La cátedra en memoria: las versiones que informa en cada lectura, los cambios publicados y el catálogo actual
	static class InMemoryCatalogChangeSource implements CatalogChangeSource {

		// Una entrada por lectura; cuando se acaban, repite la última
		private final List<PublishedVersions> publishedVersionsByRead = new ArrayList<>();
		private final Map<Long, VersionChanges> publishedChangesByVersion = new HashMap<>();
		private final Map<Long, ProfessionalCategory> publishedCategoriesById = new HashMap<>();
		private final Map<Long, Professional> publishedProfessionalsById = new HashMap<>();
		private RuntimeException failureToThrow;
		private int versionReads;

		public PublishedVersions readVersions() {

			if (failureToThrow != null) {
				throw failureToThrow;
			}
			int readIndex = Math.min(versionReads, publishedVersionsByRead.size() - 1);
			versionReads++;
			return publishedVersionsByRead.get(readIndex);

		}

		public Map<Long, VersionChanges> readChanges(long fromVersion, long toVersion) {

			Map<Long, VersionChanges> changesByVersion = new HashMap<>();
			for (long version = fromVersion; version <= toVersion; version++) {
				if (publishedChangesByVersion.containsKey(version)) {
					changesByVersion.put(version, publishedChangesByVersion.get(version));
				}
			}
			return changesByVersion;

		}

		public CurrentCatalogEntities readCurrentCatalog(IdsByEntityType idsToRead) {

			List<ProfessionalCategory> professionalCategories = new ArrayList<>();
			List<Professional> professionals = new ArrayList<>();
			Map<CatalogEntityType, Set<Long>> missingIdsByType = new EnumMap<>(CatalogEntityType.class);
			for (Long categoryId : idsToRead.getIdsByType().getOrDefault(PROFESSIONAL_CATEGORY, Set.of())) {
				if (publishedCategoriesById.containsKey(categoryId)) {
					professionalCategories.add(publishedCategoriesById.get(categoryId));
				} else {
					missingIdsByType.computeIfAbsent(PROFESSIONAL_CATEGORY, missingType -> new HashSet<>())
							.add(categoryId);
				}
			}
			for (Long professionalId : idsToRead.getIdsByType().getOrDefault(PROFESSIONAL, Set.of())) {
				if (publishedProfessionalsById.containsKey(professionalId)) {
					professionals.add(publishedProfessionalsById.get(professionalId));
				} else {
					missingIdsByType.computeIfAbsent(PROFESSIONAL, missingType -> new HashSet<>()).add(professionalId);
				}
			}
			return new CurrentCatalogEntities(professionalCategories, professionals, List.<WeeklySchedule>of(),
					new IdsByEntityType(missingIdsByType));

		}

		// Publica una versión que cambió solo al profesional 15, como hace la cátedra al editarlo
		void publishProfessionalChange(long version) {

			publishedChangesByVersion.put(version, new VersionChanges(version,
					new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(15L)))));

		}
	}

	// Escribe en el estado en memoria, como la base, y guarda cada ejecución registrada
	static class InMemoryCatalogReplicaWriter implements CatalogReplicaWriter {

		private final InMemorySyncStateRepository syncStateRepository;
		private final List<SyncRun> recordedRuns = new ArrayList<>();
		private final List<CurrentCatalogEntities> appliedChanges = new ArrayList<>();
		private InconsistentChangesException applyFailureToThrow;

		InMemoryCatalogReplicaWriter(InMemorySyncStateRepository syncStateRepository) {

			this.syncStateRepository = syncStateRepository;

		}

		public void replaceAll(CatalogSnapshot catalogSnapshot, SyncState stateAfterSnapshot, SyncRun successfulRun) {

			throw new UnsupportedOperationException("The incremental sync rebuilds through its use case");

		}

		public void applyChanges(CurrentCatalogEntities currentCatalogEntities, SyncState stateAfterChanges,
				SyncRun successfulRun) {

			if (applyFailureToThrow != null) {
				throw applyFailureToThrow;
			}
			appliedChanges.add(currentCatalogEntities);
			syncStateRepository.currentState = stateAfterChanges;
			recordedRuns.add(successfulRun);

		}

		public void recordRun(SyncState stateAfterRun, SyncRun recordedRun) {

			syncStateRepository.currentState = stateAfterRun;
			recordedRuns.add(recordedRun);

		}
	}

	// Reconstruye dejando la copia en snapshotVersion, o falla como lo haría la reconstrucción real
	static class FakeRebuildCatalogFromSnapshotUseCase implements RebuildCatalogFromSnapshotUseCase {

		private final InMemorySyncStateRepository syncStateRepository;
		private long snapshotVersion;
		private RuntimeException failureToThrow;
		private int rebuilds;

		FakeRebuildCatalogFromSnapshotUseCase(InMemorySyncStateRepository syncStateRepository) {

			this.syncStateRepository = syncStateRepository;

		}

		public SyncRun rebuildCatalogFromSnapshot(SyncTrigger trigger) {

			rebuilds++;
			if (failureToThrow != null) {
				throw failureToThrow;
			}
			syncStateRepository.currentState = syncStateRepository.currentState.afterSnapshotApplied(snapshotVersion,
					NOW);
			return null;

		}
	}

	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");
	private static final SyncState NEVER_SYNCED_STATE = new SyncState(null, null, SyncStatus.EMPTY, null, null, null,
			0, null, null);
	private static final ProfessionalCategory PEDIATRICS_CATEGORY = new ProfessionalCategory(3L, "Pediatria", null,
			true);
	private static final Professional ANA_GOMEZ = new Professional(15L, 1L, "Ana", "Gomez", true);
	private static final Professional LUIS_PEREZ = new Professional(22L, 3L, "Luis", "Perez", true);

	private final CountingSyncLock syncLock = new CountingSyncLock();
	private final InMemorySyncStateRepository syncStateRepository = new InMemorySyncStateRepository();
	private final InMemoryCatalogChangeSource catalogChangeSource = new InMemoryCatalogChangeSource();
	private final InMemoryCatalogReplicaWriter catalogReplicaWriter = new InMemoryCatalogReplicaWriter(
			syncStateRepository);
	private final FakeRebuildCatalogFromSnapshotUseCase rebuildCatalogFromSnapshotUseCase = new FakeRebuildCatalogFromSnapshotUseCase(
			syncStateRepository);
	private final SyncCatalogUseCaseImpl syncCatalogUseCase = new SyncCatalogUseCaseImpl(syncLock,
			syncStateRepository, catalogChangeSource, catalogReplicaWriter, rebuildCatalogFromSnapshotUseCase);

	@Test
	void recordsANoopWhenTheCopyIsUpToDate() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(9L, NOW);
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));

		syncCatalogUseCase.syncCatalog(SyncTrigger.NOTIFICATION);

		assertThat(catalogReplicaWriter.recordedRuns).hasSize(1);
		SyncRun noopRun = catalogReplicaWriter.recordedRuns.getFirst();
		assertThat(noopRun.getRunResult()).isEqualTo(SyncRunResult.NOOP);
		assertThat(noopRun.getTriggerSource()).isEqualTo(SyncTrigger.NOTIFICATION);
		assertThat(syncStateRepository.currentState.getRemoteVersion()).isEqualTo(9L);
		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isZero();
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void rebuildsAnEmptyCopyWithoutAskingForTheChanges() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE;
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 9L;
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));

		syncCatalogUseCase.syncCatalog(SyncTrigger.STARTUP);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		// Solo la pasada después del snapshot leyó las versiones, y no registró un NOOP
		assertThat(catalogChangeSource.versionReads).isEqualTo(1);
		assertThat(catalogReplicaWriter.recordedRuns).isEmpty();

	}

	// Caso 18.2 del anexo: la copia tiene la 3 y la cátedra ya no guarda los cambios desde la 4
	@Test
	void rebuildsWhenTheChangesAreNoLongerAvailable() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(3L, NOW);
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 7L;
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(7L, 5L));

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(7L);

	}

	@Test
	void rebuildsWhenAVersionOfTheChainIsMissing() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 9L;
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));
		// Falta la 8
		catalogChangeSource.publishProfessionalChange(9L);

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		assertThat(catalogReplicaWriter.appliedChanges).isEmpty();

	}

	@Test
	void rebuildsWhenAChangedEntityIsMissing() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 8L;
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(8L, 4L));
		// El cambio lista al profesional 15, pero el catálogo actual no lo tiene
		catalogChangeSource.publishProfessionalChange(8L);

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		assertThat(catalogReplicaWriter.appliedChanges).isEmpty();

	}

	@Test
	void appliesTheChangesFrom7To9() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));
		catalogChangeSource.publishedChangesByVersion.put(8L, new VersionChanges(8L,
				new IdsByEntityType(Map.of(PROFESSIONAL_CATEGORY, Set.of(3L), PROFESSIONAL, Set.of(22L)))));
		catalogChangeSource.publishProfessionalChange(9L);
		catalogChangeSource.publishedCategoriesById.put(3L, PEDIATRICS_CATEGORY);
		catalogChangeSource.publishedProfessionalsById.put(15L, ANA_GOMEZ);
		catalogChangeSource.publishedProfessionalsById.put(22L, LUIS_PEREZ);

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(catalogReplicaWriter.appliedChanges).hasSize(1);
		CurrentCatalogEntities appliedEntities = catalogReplicaWriter.appliedChanges.getFirst();
		assertThat(appliedEntities.getProfessionalCategories()).containsExactly(PEDIATRICS_CATEGORY);
		assertThat(appliedEntities.getProfessionals()).containsExactlyInAnyOrder(ANA_GOMEZ, LUIS_PEREZ);
		// Un solo run: la segunda pasada vio que la cátedra no publicó nada más y no registró un NOOP
		assertThat(catalogReplicaWriter.recordedRuns).hasSize(1);
		SyncRun incrementalRun = catalogReplicaWriter.recordedRuns.getFirst();
		assertThat(incrementalRun.getMode()).isEqualTo(SyncMode.INCREMENTAL);
		assertThat(incrementalRun.getRunResult()).isEqualTo(SyncRunResult.SUCCESS);
		assertThat(incrementalRun.getFromVersion()).isEqualTo(7L);
		assertThat(incrementalRun.getToVersion()).isEqualTo(9L);
		assertThat(incrementalRun.getEntitiesApplied()).isEqualTo(3);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(9L);
		assertThat(syncStateRepository.currentState.getStatus()).isEqualTo(SyncStatus.IN_SYNC);
		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isZero();

	}

	@Test
	void rebuildsWhenTheChangesBreakAReference() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 8L;
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(8L, 4L));
		catalogChangeSource.publishProfessionalChange(8L);
		catalogChangeSource.publishedProfessionalsById.put(15L, ANA_GOMEZ);
		catalogReplicaWriter.applyFailureToThrow = new InconsistentChangesException("Broken reference",
				new RuntimeException("foreign key"));

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(8L);

	}

	// La cátedra publica la 9 mientras se aplicaba la 8: la segunda pasada la trae
	@Test
	void keepsGoingWhileTheCatalogPublishes() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(8L, 4L));
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));
		catalogChangeSource.publishProfessionalChange(8L);
		catalogChangeSource.publishProfessionalChange(9L);
		catalogChangeSource.publishedProfessionalsById.put(15L, ANA_GOMEZ);

		syncCatalogUseCase.syncCatalog(SyncTrigger.NOTIFICATION);

		assertThat(catalogReplicaWriter.recordedRuns).hasSize(2);
		assertThat(catalogReplicaWriter.recordedRuns.get(0).getToVersion()).isEqualTo(8L);
		assertThat(catalogReplicaWriter.recordedRuns.get(1).getFromVersion()).isEqualTo(8L);
		assertThat(catalogReplicaWriter.recordedRuns.get(1).getToVersion()).isEqualTo(9L);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(9L);

	}

	// Una versión nueva en cada lectura: se detiene a las cinco pasadas en lugar de seguir para siempre
	@Test
	void stopsAfterTheMaximumPasses() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		for (long publishedVersion = 8L; publishedVersion <= 20L; publishedVersion++) {
			catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(publishedVersion, 4L));
			catalogChangeSource.publishProfessionalChange(publishedVersion);
		}
		catalogChangeSource.publishedProfessionalsById.put(15L, ANA_GOMEZ);

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(catalogReplicaWriter.recordedRuns).hasSize(5);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(12L);
		assertThat(syncLock.releases).isEqualTo(1);

	}

	// Si después del snapshot la cadena sigue rota, un segundo snapshot traería lo mismo
	@Test
	void stopsInsteadOfRebuildingTwice() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		rebuildCatalogFromSnapshotUseCase.snapshotVersion = 7L;
		// La cátedra informa la 9, pero nunca publicó los cambios de la 8 ni de la 9
		catalogChangeSource.publishedVersionsByRead.add(new PublishedVersions(9L, 4L));

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		assertThat(rebuildCatalogFromSnapshotUseCase.rebuilds).isEqualTo(1);
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void recordsAFailureWhenTheSourceDoesNotAnswer() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		CatalogSourceUnavailableException unavailableException = new CatalogSourceUnavailableException(
				"Could not read catedra:sync:metadata");
		catalogChangeSource.failureToThrow = unavailableException;

		assertThatThrownBy(() -> syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED)).isSameAs(unavailableException);

		assertThat(catalogReplicaWriter.recordedRuns).hasSize(1);
		SyncRun failedRun = catalogReplicaWriter.recordedRuns.getFirst();
		assertThat(failedRun.getMode()).isEqualTo(SyncMode.INCREMENTAL);
		assertThat(failedRun.getRunResult()).isEqualTo(SyncRunResult.FAILED);
		assertThat(failedRun.getErrorCode()).isEqualTo("CATALOG_SOURCE_UNAVAILABLE");
		assertThat(syncStateRepository.currentState.getStatus()).isEqualTo(SyncStatus.DEGRADED);
		assertThat(syncStateRepository.currentState.getAppliedVersion()).isEqualTo(7L);
		assertThat(syncLock.releases).isEqualTo(1);

	}

	// La reconstrucción ya registró su propia falla: la incremental no registra otra
	@Test
	void letsASnapshotFailurePassWithoutRecordingItTwice() {

		syncStateRepository.currentState = NEVER_SYNCED_STATE;
		CatalogSourceUnavailableException snapshotFailure = new CatalogSourceUnavailableException(
				"Could not reach /api/synchronization/snapshot");
		rebuildCatalogFromSnapshotUseCase.failureToThrow = snapshotFailure;

		assertThatThrownBy(() -> syncCatalogUseCase.syncCatalog(SyncTrigger.STARTUP)).isSameAs(snapshotFailure);

		assertThat(catalogReplicaWriter.recordedRuns).isEmpty();
		assertThat(syncLock.releases).isEqualTo(1);

	}

	@Test
	void doesNothingWhileAnotherSyncIsRunning() {

		syncLock.takenByAnotherSync = true;

		assertThatThrownBy(() -> syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED))
				.isInstanceOf(SyncAlreadyRunningException.class);

		assertThat(catalogChangeSource.versionReads).isZero();
		assertThat(catalogReplicaWriter.recordedRuns).isEmpty();
		// No libera un lock que no tomó
		assertThat(syncLock.releases).isZero();

	}
}
