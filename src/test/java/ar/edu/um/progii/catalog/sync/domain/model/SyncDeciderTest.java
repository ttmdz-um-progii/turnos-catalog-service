package ar.edu.um.progii.catalog.sync.domain.model;

import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL_CATEGORY;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.WEEKLY_SCHEDULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SyncDeciderTest {

	private static VersionChanges versionChanges(long declaredVersion, Map<CatalogEntityType, Set<Long>> changedIds) {

		return VersionChanges.builder().version(declaredVersion).changedIds(new IdsByEntityType(changedIds)).build();

	}

	// Cadena completa de la versión 7 a la 9, con localVersion = 6
	private static Map<Long, VersionChanges> changesFromVersion7To9() {

		return Map.of(
				7L, versionChanges(7, Map.of(PROFESSIONAL_CATEGORY, Set.of(1L), PROFESSIONAL, Set.of(10L))),
				8L, versionChanges(8, Map.of(PROFESSIONAL, Set.of(10L, 11L))),
				9L, versionChanges(9, Map.of(WEEKLY_SCHEDULE, Set.of(100L))));

	}

	// El INCREMENTAL que la etapa 2 devuelve para changesFromVersion7To9()
	private static SyncDecision incrementalFromVersion7To9() {

		return SyncDecision.incremental(new IdsByEntityType(Map.of(
				PROFESSIONAL_CATEGORY, Set.of(1L),
				PROFESSIONAL, Set.of(10L, 11L),
				WEEKLY_SCHEDULE, Set.of(100L))));

	}

	// Etapa 1: decideByVersions

	@Test
	void snapshotWhenNeverSynced() {

		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(null, 9, 1);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	@Test
	void noopWhenAlreadyAtCurrentVersion() {

		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(9L, 9, 1);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.NOOP);

	}

	@Test
	void noopWhenNoticeIsOlderThanLocalVersion() {

		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(9L, 7, 1);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.NOOP);

	}

	@Test
	void snapshotWhenLocalIsOlderThanOldestAvailable() {

		// Caso 18.2 del anexo: localVersion = 3 y oldestVersion = 4; no se puede avanzar de a cambios desde la 4
		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(3L, 5, 4);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	@Test
	void noopBeatsDiscontinuityForDuplicateNotice() {

		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(3L, 3, 4);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.NOOP);

	}

	@Test
	void decideByVersionsNeedsChainWhenIncrementalIsPossible() {

		assertThat(SyncDecider.decideByVersions(6L, 9, 4)).isEmpty();
		assertThat(SyncDecider.decideByVersions(4L, 9, 4)).isEmpty();

	}

	@Test
	void snapshotWhenOldestIsAheadOfCurrent() {

		// Datos incoherentes de la cátedra: ante la duda, se reconstruye todo
		Optional<SyncDecision> decisionFromVersions = SyncDecider.decideByVersions(5L, 8, 10);

		assertThat(decisionFromVersions).isPresent();
		assertThat(decisionFromVersions.get().getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	// Etapa 2: decideByChain

	@Test
	void snapshotWhenAVersionInTheRangeIsMissing() {

		Map<Long, VersionChanges> changesByVersion = Map.of(
				7L, versionChanges(7, Map.of()),
				9L, versionChanges(9, Map.of()));

		SyncDecision decision = SyncDecider.decideByChain(6L, 9, changesByVersion);

		assertThat(decision.getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	@Test
	void snapshotWhenDeclaredVersionDoesNotMatchItsKey() {

		Map<Long, VersionChanges> changesByVersion = Map.of(
				7L, versionChanges(7, Map.of()),
				8L, versionChanges(7, Map.of()),
				9L, versionChanges(9, Map.of()));

		SyncDecision decision = SyncDecider.decideByChain(6L, 9, changesByVersion);

		assertThat(decision.getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	@Test
	void incrementalWithUnionOfIdsWhenChainIsComplete() {

		SyncDecision decision = SyncDecider.decideByChain(6L, 9, changesFromVersion7To9());

		assertThat(decision.getAction()).isEqualTo(SyncAction.INCREMENTAL);
		assertThat(decision.getIdsToApply()).isEqualTo(incrementalFromVersion7To9().getIdsToApply());

	}

	@Test
	void incrementalWithoutIdsWhenRangeHasNoChanges() {

		Map<Long, VersionChanges> changesByVersion = Map.of(7L, versionChanges(7, Map.of()));

		SyncDecision decision = SyncDecider.decideByChain(6L, 7, changesByVersion);

		assertThat(decision.getAction()).isEqualTo(SyncAction.INCREMENTAL);
		assertThat(decision.getIdsToApply().isEmpty()).isTrue();

	}

	@Test
	void versionsOutsideTheRangeAreIgnored() {

		Map<Long, VersionChanges> changesByVersion = Map.of(
				6L, versionChanges(6, Map.of(PROFESSIONAL, Set.of(50L))),
				7L, versionChanges(7, Map.of(PROFESSIONAL, Set.of(10L))),
				8L, versionChanges(8, Map.of(PROFESSIONAL, Set.of(60L))));

		SyncDecision decision = SyncDecider.decideByChain(6L, 7, changesByVersion);

		assertThat(decision.getIdsToApply()).isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L))));

	}

	@Test
	void decideByChainRejectsALocalVersionThatIsNotBehind() {

		// La etapa 1 ya habría devuelto NOOP: seguir haría retroceder la copia local
		assertThatThrownBy(() -> SyncDecider.decideByChain(9L, 7, Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SyncDecider.decideByChain(9L, 9, Map.of()))
				.isInstanceOf(IllegalArgumentException.class);

	}

	// Etapa 3: decideByMissingIds

	@Test
	void snapshotWhenAPendingIdIsMissingFromCurrentCatalog() {

		SyncDecision decision = SyncDecider.decideByMissingIds(incrementalFromVersion7To9(),
				new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(11L))));

		assertThat(decision.getAction()).isEqualTo(SyncAction.SNAPSHOT);

	}

	@Test
	void missingIdsThatAreNotPendingDoNotForceSnapshot() {

		// 11 falta pero como categoría, y 999 no está entre los pendientes
		IdsByEntityType idsMissingFromCurrentCatalog = new IdsByEntityType(Map.of(
				PROFESSIONAL_CATEGORY, Set.of(11L),
				PROFESSIONAL, Set.of(999L)));

		SyncDecision decision = SyncDecider.decideByMissingIds(incrementalFromVersion7To9(),
				idsMissingFromCurrentCatalog);

		assertThat(decision.getAction()).isEqualTo(SyncAction.INCREMENTAL);

	}

	@Test
	void incrementalIsConfirmedWhenNoPendingIdIsMissing() {

		SyncDecision incrementalDecision = incrementalFromVersion7To9();

		SyncDecision decision = SyncDecider.decideByMissingIds(incrementalDecision, IdsByEntityType.empty());

		assertThat(decision).isSameAs(incrementalDecision);

	}

	@Test
	void decideByMissingIdsOnlyAcceptsAnIncremental() {

		assertThatThrownBy(() -> SyncDecider.decideByMissingIds(SyncDecision.snapshot(), IdsByEntityType.empty()))
				.isInstanceOf(IllegalArgumentException.class);

	}
}
