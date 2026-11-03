package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SyncDecisionTest {

	@Test
	void noopAndSnapshotCarryTheirActionWithoutIds() {

		assertThat(SyncDecision.noop().getAction()).isEqualTo(SyncAction.NOOP);
		assertThat(SyncDecision.noop().getIdsToApply().isEmpty()).isTrue();
		assertThat(SyncDecision.snapshot().getAction()).isEqualTo(SyncAction.SNAPSHOT);
		assertThat(SyncDecision.snapshot().getIdsToApply().isEmpty()).isTrue();

	}

	@Test
	void incrementalCarriesItsIds() {

		IdsByEntityType idsToApply = new IdsByEntityType(Map.of(CatalogEntityType.PROFESSIONAL, Set.of(10L)));

		SyncDecision decision = SyncDecision.incremental(idsToApply);

		assertThat(decision.getAction()).isEqualTo(SyncAction.INCREMENTAL);
		assertThat(decision.getIdsToApply()).isEqualTo(idsToApply);

	}

	@Test
	void incrementalAcceptsNoIds() {

		SyncDecision decision = SyncDecision.incremental(IdsByEntityType.empty());

		assertThat(decision.getAction()).isEqualTo(SyncAction.INCREMENTAL);
		assertThat(decision.getIdsToApply().isEmpty()).isTrue();

	}

	@Test
	void incrementalRejectsNullIds() {

		assertThatThrownBy(() -> SyncDecision.incremental(null)).isInstanceOf(IllegalArgumentException.class);

	}
}
