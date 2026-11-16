package ar.edu.um.progii.catalog.sync.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ar.edu.um.progii.catalog.KafkaTestcontainersConfiguration;
import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

// De punta a punta, con Kafka, Redis y PostgreSQL en contenedores: la copia está en la versión 7 y la cátedra publicó
// la 8. El listener se enciende solo acá, y lee el topic desde el principio para no perder el primer aviso mientras se
// suscribe
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@SpringBootTest(properties = {"catalog.sync.notification-listener-enabled=true",
		"spring.kafka.consumer.group-id=catalog-test",
		"spring.kafka.consumer.auto-offset-reset=earliest", "catedra.kafka.catalog-topic=catedra.catalog.test",
		"catedra.redis.retry-delays=10ms,10ms,10ms"})
class CatalogUpdatedEventListenerIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");
	// Con un campo que este servicio no conoce: el anexo §15.1 pide ignorarlo
	private static final String CATALOG_UPDATED_VERSION_8 = "{\"eventId\":\"catalog-event-8-4f3b\","
			+ "\"eventType\":\"CatalogUpdated\",\"newVersion\":8,\"occurredAt\":\"2026-09-10T13:44:59Z\","
			+ "\"source\":\"MANUAL\",\"schemaVersion\":1,\"futureField\":true}";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private CatalogReplicaWriter catalogReplicaWriter;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void loadVersion7LocallyAndVersion8InTheCatalog() {

		CatalogSnapshot snapshotVersion7 = new CatalogSnapshot(7L,
				List.of(new ProfessionalCategory(1L, "Clinica medica", "Atencion clinica general", true)), List.of(),
				List.of());
		SyncState neverSyncedState = new SyncState(null, null, SyncStatus.EMPTY, null, null, null, 0, null, null);
		catalogReplicaWriter.replaceAll(snapshotVersion7, neverSyncedState.afterSnapshotApplied(7L, NOW),
				SyncRun.builder()
						.triggerSource(SyncTrigger.STARTUP)
						.mode(SyncMode.FULL)
						.toVersion(7L)
						.runResult(SyncRunResult.SUCCESS)
						.entitiesApplied(1)
						.startedAt(NOW)
						.finishedAt(NOW)
						.build());

		// La 8 agrega la categoría 3 (anexo §14)
		redisTemplate.opsForHash()
				.putAll("catedra:sync:metadata",
						Map.of("currentVersion", "8", "oldestAvailableVersion", "4", "publishedAt", NOW.toString()));
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:8", "{\"version\":8,\"changes\":{\"professionalCategories\":[3],"
						+ "\"professionals\":[],\"weeklySchedules\":[]}}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professional-categories", "3",
						"{\"id\":3,\"name\":\"Pediatria\",\"description\":null,\"enabled\":true}");

	}

	@AfterEach
	void restoreTheMigratedDatabaseAndEmptyRedis() {

		emptyTheCatalogInRedis();
		jdbcTemplate.update("DELETE FROM weekly_schedule");
		jdbcTemplate.update("DELETE FROM professional");
		jdbcTemplate.update("DELETE FROM professional_category");
		jdbcTemplate.update("DELETE FROM sync_run");
		jdbcTemplate.update("UPDATE sync_state SET applied_version = NULL, remote_version = NULL, status = 'EMPTY', "
				+ "last_success_at = NULL, last_attempt_at = NULL, last_full_sync_at = NULL, consecutive_failures = 0, "
				+ "last_error_code = NULL, last_error_message = NULL");

	}

	// La entrega de Kafka es al menos una vez (anexo §18.1). Entre los dos avisos se vacía Redis: si el segundo leyera
	// la cátedra, terminaría en FAILED
	@Test
	void syncsTheFirstNotificationAndRecordsTheRepeatedOneAsNoop() {

		kafkaTemplate.send("catedra.catalog.test", "8", CATALOG_UPDATED_VERSION_8);
		await().atMost(Duration.ofSeconds(30)).until(() -> countRunsOfTheEvent() == 1);

		Map<String, Object> firstRunRow = jdbcTemplate
				.queryForMap("SELECT * FROM sync_run WHERE event_id = 'catalog-event-8-4f3b'");
		assertThat(firstRunRow.get("trigger_source")).isEqualTo("NOTIFICATION");
		assertThat(firstRunRow.get("result")).isEqualTo("SUCCESS");
		assertThat(firstRunRow.get("from_version")).isEqualTo(7L);
		assertThat(firstRunRow.get("to_version")).isEqualTo(8L);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional_category ORDER BY id", Long.class))
				.containsExactly(1L, 3L);

		emptyTheCatalogInRedis();
		kafkaTemplate.send("catedra.catalog.test", "8", CATALOG_UPDATED_VERSION_8);
		await().atMost(Duration.ofSeconds(30)).until(() -> countRunsOfTheEvent() == 2);

		List<String> runResults = jdbcTemplate.queryForList(
				"SELECT result FROM sync_run WHERE event_id = 'catalog-event-8-4f3b' ORDER BY id", String.class);
		assertThat(runResults).containsExactly("SUCCESS", "NOOP");
		assertThat(jdbcTemplate.queryForObject("SELECT applied_version FROM sync_state", Long.class)).isEqualTo(8L);

	}

	private int countRunsOfTheEvent() {

		return jdbcTemplate.queryForObject("SELECT count(*) FROM sync_run WHERE event_id = 'catalog-event-8-4f3b'",
				Integer.class);

	}

	private void emptyTheCatalogInRedis() {

		Set<String> catalogKeys = redisTemplate.keys("catedra:sync:*");
		redisTemplate.delete(catalogKeys);

	}
}
