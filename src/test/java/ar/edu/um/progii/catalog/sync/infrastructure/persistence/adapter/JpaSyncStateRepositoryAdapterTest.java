package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaSyncStateRepositoryAdapterTest {

	@Autowired
	private JpaSyncStateRepositoryAdapter jpaSyncStateRepositoryAdapter;

	// La migración deja la única fila con la réplica nunca sincronizada
	@Test
	void readsTheInitialStateLeftByTheMigration() {

		SyncState currentState = jpaSyncStateRepositoryAdapter.findCurrent();

		assertThat(currentState.getAppliedVersion()).isNull();
		assertThat(currentState.getStatus()).isEqualTo(SyncStatus.EMPTY);
		assertThat(currentState.getConsecutiveFailures()).isZero();

	}
}
