package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CatalogSyncStateTest {

	// El estado con el que arranca la réplica: nunca sincronizada y sin fechas ni errores
	@Test
	void acceptsTheNeverSyncedState() {

		assertThatCode(() -> new CatalogSyncState(null, null, SyncStatus.EMPTY, null, null, null, 0, null, null))
				.doesNotThrowAnyException();

	}

	@Test
	void rejectsNullStatus() {

		assertThatThrownBy(() -> new CatalogSyncState(null, null, null, null, null, null, 0, null, null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNegativeConsecutiveFailures() {

		assertThatThrownBy(() -> new CatalogSyncState(null, null, SyncStatus.EMPTY, null, null, null, -1, null, null))
				.isInstanceOf(IllegalArgumentException.class);

	}
}
