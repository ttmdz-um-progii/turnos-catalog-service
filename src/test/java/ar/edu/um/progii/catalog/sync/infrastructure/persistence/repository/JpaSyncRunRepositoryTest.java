package ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.SyncRunEntity;

// Cada test hace rollback al terminar (@Transactional), así no deja filas para los demás
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaSyncRunRepositoryTest {

	private static final Instant STARTED_AT = Instant.parse("2026-09-10T13:44:59Z");
	private static final Instant FINISHED_AT = Instant.parse("2026-09-10T13:45:01Z");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaSyncRunRepository jpaSyncRunRepository;

	@Test
	void readsARunWithTheIdGeneratedByTheDatabase() {

		Long generatedId = jdbcTemplate.queryForObject(
				"INSERT INTO sync_run (trigger_source, event_id, mode, from_version, to_version, result, entities_applied, started_at, finished_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
				Long.class, "NOTIFICATION", "event-1", "INCREMENTAL", 6L, 9L, "SUCCESS", 3,
				Timestamp.from(STARTED_AT), Timestamp.from(FINISHED_AT));

		SyncRunEntity syncRun = jpaSyncRunRepository.findById(generatedId).orElseThrow();

		assertThat(syncRun.getId()).isEqualTo(generatedId);
		assertThat(syncRun.getTriggerSource()).isEqualTo(SyncTrigger.NOTIFICATION);
		assertThat(syncRun.getEventId()).isEqualTo("event-1");
		assertThat(syncRun.getMode()).isEqualTo(SyncMode.INCREMENTAL);
		assertThat(syncRun.getFromVersion()).isEqualTo(6L);
		assertThat(syncRun.getToVersion()).isEqualTo(9L);
		assertThat(syncRun.getRunResult()).isEqualTo(SyncRunResult.SUCCESS);
		assertThat(syncRun.getEntitiesApplied()).isEqualTo(3);
		assertThat(syncRun.getStartedAt()).isEqualTo(STARTED_AT);
		assertThat(syncRun.getFinishedAt()).isEqualTo(FINISHED_AT);
		assertThat(syncRun.getErrorCode()).isNull();
		assertThat(syncRun.getErrorMessage()).isNull();

	}
}
