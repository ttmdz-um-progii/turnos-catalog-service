package ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.CatalogSyncStateEntity;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaCatalogSyncStateRepositoryTest {

	@Autowired
	private JpaCatalogSyncStateRepository jpaCatalogSyncStateRepository;

	// La migración deja la única fila con la réplica nunca sincronizada
	@Test
	void readsTheInitialStateLeftByTheMigration() {

		CatalogSyncStateEntity syncState = jpaCatalogSyncStateRepository.findById((short) 1).orElseThrow();

		assertThat(syncState.getId()).isEqualTo((short) 1);
		assertThat(syncState.getAppliedVersion()).isNull();
		assertThat(syncState.getRemoteVersion()).isNull();
		assertThat(syncState.getStatus()).isEqualTo(SyncStatus.EMPTY);
		assertThat(syncState.getLastSuccessAt()).isNull();
		assertThat(syncState.getLastAttemptAt()).isNull();
		assertThat(syncState.getLastFullSyncAt()).isNull();
		assertThat(syncState.getConsecutiveFailures()).isZero();
		assertThat(syncState.getLastErrorCode()).isNull();
		assertThat(syncState.getLastErrorMessage()).isNull();

	}
}
