package ar.edu.um.progii.catalog.sync.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
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

// De punta a punta, con el PostgreSQL y el Redis de TestcontainersConfiguration: la copia está en la versión 7 y la
// cátedra publicó la 8 y la 9. Las mismas propiedades que el test del adaptador Redis, así comparten el contexto
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "catedra.redis.retry-delays=10ms,10ms,10ms")
class SyncCatalogUseCaseImplIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");

	@Autowired
	private SyncCatalogUseCase syncCatalogUseCase;

	@Autowired
	private CatalogReplicaWriter catalogReplicaWriter;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void loadVersion7LocallyAndVersion9InTheCatalog() {

		CatalogSnapshot snapshotVersion7 = new CatalogSnapshot(7L,
				List.of(new ProfessionalCategory(1L, "Clinica medica", "Atencion clinica general", true)),
				List.of(new Professional(15L, 1L, "Ana", "Gómez", true)), List.of(new WeeklySchedule(21L, 15L,
						DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true)));
		SyncState neverSyncedState = new SyncState(null, null, SyncStatus.EMPTY, null, null, null, 0, null, null);
		catalogReplicaWriter.replaceAll(snapshotVersion7, neverSyncedState.afterSnapshotApplied(7L, NOW),
				SyncRun.builder()
						.triggerSource(SyncTrigger.STARTUP)
						.mode(SyncMode.FULL)
						.toVersion(7L)
						.runResult(SyncRunResult.SUCCESS)
						.entitiesApplied(3)
						.startedAt(NOW)
						.finishedAt(NOW)
						.build());

		// La 8 agrega la categoría 3 y al profesional 22; la 9 cambia el apellido del 15 (anexo §14)
		redisTemplate.opsForHash()
				.putAll("catedra:sync:metadata",
						Map.of("currentVersion", "9", "oldestAvailableVersion", "4", "publishedAt", NOW.toString()));
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:8",
						"{\"version\":8,\"changes\":{\"professionalCategories\":[3],\"professionals\":[22],"
								+ "\"weeklySchedules\":[]}}");
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:9",
						"{\"version\":9,\"changes\":{\"professionalCategories\":[],\"professionals\":[15],"
								+ "\"weeklySchedules\":[]}}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professional-categories", "3",
						"{\"id\":3,\"name\":\"Pediatria\",\"description\":null,\"enabled\":true}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professionals", "22",
						"{\"id\":22,\"categoryId\":3,\"firstName\":\"Luis\",\"lastName\":\"Perez\",\"enabled\":true}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professionals", "15",
						"{\"id\":15,\"categoryId\":1,\"firstName\":\"Ana\",\"lastName\":\"Gómez Paz\",\"enabled\":true}");

	}

	@AfterEach
	void restoreTheMigratedDatabaseAndEmptyRedis() {

		Set<String> catalogKeys = redisTemplate.keys("catedra:sync:*");
		redisTemplate.delete(catalogKeys);
		jdbcTemplate.update("DELETE FROM weekly_schedule");
		jdbcTemplate.update("DELETE FROM professional");
		jdbcTemplate.update("DELETE FROM professional_category");
		jdbcTemplate.update("DELETE FROM sync_run");
		jdbcTemplate.update("UPDATE sync_state SET applied_version = NULL, remote_version = NULL, status = 'EMPTY', "
				+ "last_success_at = NULL, last_attempt_at = NULL, last_full_sync_at = NULL, consecutive_failures = 0, "
				+ "last_error_code = NULL, last_error_message = NULL");

	}

	@Test
	void bringsTheCopyFromVersion7To9() {

		syncCatalogUseCase.syncCatalog(SyncTrigger.SCHEDULED);

		Map<String, Object> syncStateRow = jdbcTemplate.queryForMap("SELECT * FROM sync_state");
		assertThat(syncStateRow.get("applied_version")).isEqualTo(9L);
		assertThat(syncStateRow.get("remote_version")).isEqualTo(9L);
		assertThat(syncStateRow.get("status")).isEqualTo("IN_SYNC");
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional_category ORDER BY id", Long.class))
				.containsExactly(1L, 3L);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional ORDER BY id", Long.class))
				.containsExactly(15L, 22L);
		assertThat(jdbcTemplate.queryForObject("SELECT search_name FROM professional WHERE id = 15", String.class))
				.isEqualTo("ana gomez paz");
		// El horario no cambió y sigue ahí
		assertThat(jdbcTemplate.queryForList("SELECT id FROM weekly_schedule", Long.class)).containsExactly(21L);
		Map<String, Object> incrementalRunRow = jdbcTemplate
				.queryForMap("SELECT * FROM sync_run WHERE mode = 'INCREMENTAL'");
		assertThat(incrementalRunRow.get("result")).isEqualTo("SUCCESS");
		assertThat(incrementalRunRow.get("from_version")).isEqualTo(7L);
		assertThat(incrementalRunRow.get("to_version")).isEqualTo(9L);
		assertThat(incrementalRunRow.get("entities_applied")).isEqualTo(3);

	}
}
