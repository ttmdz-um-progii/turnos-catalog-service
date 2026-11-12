package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Cada test hace rollback al terminar (@Transactional), así no deja filas para los demás
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaSyncRunRepositoryAdapterTest {

	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaSyncRunRepositoryAdapter jpaSyncRunRepositoryAdapter;

	@Test
	void findsAnEventThatEndedInSuccessOrNoop() {

		insertNotificationRun("catalog-event-8", "SUCCESS");
		insertNotificationRun("catalog-event-9", "NOOP");

		assertThat(jpaSyncRunRepositoryAdapter.existsCompletedRunForEvent("catalog-event-8")).isTrue();
		assertThat(jpaSyncRunRepositoryAdapter.existsCompletedRunForEvent("catalog-event-9")).isTrue();

	}

	// Un aviso que falló se tiene que poder reintentar, y uno que nunca llegó no está
	@Test
	void ignoresAFailedEventAndAnUnknownOne() {

		insertNotificationRun("catalog-event-8", "FAILED");

		assertThat(jpaSyncRunRepositoryAdapter.existsCompletedRunForEvent("catalog-event-8")).isFalse();
		assertThat(jpaSyncRunRepositoryAdapter.existsCompletedRunForEvent("catalog-event-10")).isFalse();

	}

	private void insertNotificationRun(String eventId, String runResult) {

		jdbcTemplate.update(
				"INSERT INTO sync_run (trigger_source, event_id, mode, result, entities_applied, started_at, finished_at) VALUES ('NOTIFICATION', ?, 'INCREMENTAL', ?, 0, ?, ?)",
				eventId, runResult, Timestamp.from(NOW), Timestamp.from(NOW));

	}
}
