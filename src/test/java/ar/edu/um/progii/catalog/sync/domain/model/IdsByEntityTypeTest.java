package ar.edu.um.progii.catalog.sync.domain.model;

import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL_CATEGORY;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.WEEKLY_SCHEDULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdsByEntityTypeTest {

	@Test
	void rejectsNullIds() {

		assertThatThrownBy(() -> new IdsByEntityType(null)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullIdsOfAType() {

		Map<CatalogEntityType, Set<Long>> idsByType = new HashMap<>();
		idsByType.put(PROFESSIONAL, null);

		assertThatThrownBy(() -> new IdsByEntityType(idsByType)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullEntityType() {

		Map<CatalogEntityType, Set<Long>> idsByType = new HashMap<>();
		idsByType.put(null, Set.of(10L));

		assertThatThrownBy(() -> new IdsByEntityType(idsByType)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void keepsAnUnmodifiableCopyOfTheIds() {

		Set<Long> professionalIds = new HashSet<>(Set.of(10L));
		Map<CatalogEntityType, Set<Long>> idsByType = new HashMap<>(Map.of(PROFESSIONAL, professionalIds));

		IdsByEntityType ids = new IdsByEntityType(idsByType);
		professionalIds.add(11L);
		idsByType.put(WEEKLY_SCHEDULE, Set.of(100L));

		assertThat(ids.getIdsByType()).isEqualTo(Map.of(PROFESSIONAL, Set.of(10L)));
		assertThatThrownBy(() -> ids.getIdsByType().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> ids.getIdsByType().get(PROFESSIONAL).clear())
				.isInstanceOf(UnsupportedOperationException.class);

	}

	@Test
	void typesWithoutIdsAreNotKept() {

		IdsByEntityType ids = new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L), WEEKLY_SCHEDULE, Set.of()));

		assertThat(ids).isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L))));
		assertThat(new IdsByEntityType(Map.of(WEEKLY_SCHEDULE, Set.of())).isEmpty()).isTrue();

	}

	@Test
	void unionMergesByTypeWithoutDuplicates() {

		IdsByEntityType firstIds = new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L, 11L)));
		IdsByEntityType secondIds = new IdsByEntityType(
				Map.of(PROFESSIONAL, Set.of(11L, 25L), WEEKLY_SCHEDULE, Set.of(3L)));

		IdsByEntityType mergedIds = firstIds.union(secondIds);

		assertThat(mergedIds)
				.isEqualTo(
						new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L, 11L, 25L), WEEKLY_SCHEDULE, Set.of(3L))));

	}

	@Test
	void unionDoesNotModifyTheOriginalGroups() {

		IdsByEntityType firstIds = new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L)));
		IdsByEntityType secondIds = new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(25L)));

		firstIds.union(secondIds);

		assertThat(firstIds).isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L))));
		assertThat(secondIds).isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(25L))));

	}

	@Test
	void containsAnyOfComparesIdsOfTheSameType() {

		IdsByEntityType pendingIds = new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(10L, 11L)));

		assertThat(pendingIds.containsAnyOf(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(11L))))).isTrue();
		// El 11 existe pero como categoría, no como profesional
		assertThat(pendingIds.containsAnyOf(new IdsByEntityType(Map.of(PROFESSIONAL_CATEGORY, Set.of(11L)))))
				.isFalse();
		assertThat(pendingIds.containsAnyOf(IdsByEntityType.empty())).isFalse();

	}
}
