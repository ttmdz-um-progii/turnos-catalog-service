package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VersionChangesTest {

	@Test
	void builderSetsEveryField() {

		IdsByEntityType changedIds = new IdsByEntityType(Map.of(CatalogEntityType.PROFESSIONAL, Set.of(10L)));

		VersionChanges changes = VersionChanges.builder()
				.version(7)
				.changedIds(changedIds)
				.build();

		assertThat(changes.getVersion()).isEqualTo(7);
		assertThat(changes.getChangedIds()).isEqualTo(changedIds);

	}

	@Test
	void builderGoesThroughValidation() {

		assertThatThrownBy(() -> VersionChanges.builder().version(7).build())
				.isInstanceOf(IllegalArgumentException.class);

	}
}
