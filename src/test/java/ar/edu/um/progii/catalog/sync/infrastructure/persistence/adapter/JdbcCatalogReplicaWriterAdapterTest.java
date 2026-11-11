package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.InconsistentChangesException;
import ar.edu.um.progii.catalog.sync.application.exception.ReplicaWriteFailedException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Sin @Transactional en el test: el rollback que se prueba es el del writer, y cada test confirma lo que escribe.
// Por eso @AfterEach deja la base como la deja la migración
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JdbcCatalogReplicaWriterAdapterTest {

	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");
	private static final SyncState NEVER_SYNCED_STATE = new SyncState(null, null, SyncStatus.EMPTY, null, null, null,
			0, null, null);
	// Categoría 1, profesional 15 con su horario 21
	private static final CatalogSnapshot SNAPSHOT_VERSION_7 = new CatalogSnapshot(7L,
			List.of(new ProfessionalCategory(1L, "Clinica medica", "Atencion clinica general", true)),
			List.of(new Professional(15L, 1L, "Ana", "Gómez", true)), List.of(new WeeklySchedule(21L, 15L,
					DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true)));
	// La cátedra sacó al profesional 15 y su horario, y sumó al 22 sin horarios
	private static final CatalogSnapshot SNAPSHOT_VERSION_8 = new CatalogSnapshot(8L,
			List.of(new ProfessionalCategory(1L, "Clinica medica", "Atencion clinica general", true)),
			List.of(new Professional(22L, 1L, "Luis", "Perez", true)), List.of());

	@Autowired
	private JdbcCatalogReplicaWriterAdapter jdbcCatalogReplicaWriterAdapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void restoreTheMigratedDatabase() {

		jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_sync_run_insert ON sync_run");
		jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_sync_run_insert()");
		jdbcTemplate.update("DELETE FROM weekly_schedule");
		jdbcTemplate.update("DELETE FROM professional");
		jdbcTemplate.update("DELETE FROM professional_category");
		jdbcTemplate.update("DELETE FROM sync_run");
		jdbcTemplate.update("UPDATE sync_state SET applied_version = NULL, remote_version = NULL, status = 'EMPTY', "
				+ "last_success_at = NULL, last_attempt_at = NULL, last_full_sync_at = NULL, consecutive_failures = 0, "
				+ "last_error_code = NULL, last_error_message = NULL");

	}

	@Test
	void appliesTheSnapshotToAnEmptyCatalog() {

		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7,
				NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW), successfulRun(null, SNAPSHOT_VERSION_7));

		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional_category", Long.class)).containsExactly(1L);
		Map<String, Object> professionalRow = jdbcTemplate.queryForMap("SELECT * FROM professional WHERE id = 15");
		assertThat(professionalRow.get("category_id")).isEqualTo(1L);
		assertThat(professionalRow.get("last_name")).isEqualTo("Gómez");
		assertThat(professionalRow.get("search_name")).isEqualTo("ana gomez");
		Map<String, Object> weeklyScheduleRow = jdbcTemplate.queryForMap(
				"SELECT * FROM weekly_schedule WHERE id = 21");
		assertThat(weeklyScheduleRow.get("day_of_week")).isEqualTo("MONDAY");
		assertThat(weeklyScheduleRow.get("start_time").toString()).isEqualTo("09:00:00");

		Map<String, Object> syncStateRow = jdbcTemplate.queryForMap("SELECT * FROM sync_state");
		assertThat(syncStateRow.get("applied_version")).isEqualTo(7L);
		assertThat(syncStateRow.get("status")).isEqualTo("IN_SYNC");
		assertThat(syncStateRow.get("last_full_sync_at")).isNotNull();
		Map<String, Object> syncRunRow = jdbcTemplate.queryForMap("SELECT * FROM sync_run");
		assertThat(syncRunRow.get("mode")).isEqualTo("FULL");
		assertThat(syncRunRow.get("result")).isEqualTo("SUCCESS");
		assertThat(syncRunRow.get("to_version")).isEqualTo(7L);
		assertThat(syncRunRow.get("entities_applied")).isEqualTo(3);

	}

	@Test
	void removesWhatTheNewSnapshotDoesNotBring() {

		SyncState stateAfterVersion7 = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7, stateAfterVersion7,
				successfulRun(null, SNAPSHOT_VERSION_7));

		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_8, stateAfterVersion7.afterSnapshotApplied(8L, NOW),
				successfulRun(7L, SNAPSHOT_VERSION_8));

		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional", Long.class)).containsExactly(22L);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM weekly_schedule", Long.class)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT applied_version FROM sync_state", Long.class)).isEqualTo(8L);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sync_run", Long.class)).isEqualTo(2L);

	}

	// Un trigger de prueba hace fallar el INSERT de sync_run, la última sentencia: para entonces la réplica ya se
	// borró y se volvió a escribir dentro de la transacción
	@Test
	void keepsThePreviousCatalogWhenTheWriteFails() {

		SyncState stateAfterVersion7 = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7, stateAfterVersion7,
				successfulRun(null, SNAPSHOT_VERSION_7));
		jdbcTemplate.execute("CREATE FUNCTION fail_sync_run_insert() RETURNS trigger AS $$ "
				+ "BEGIN RAISE EXCEPTION 'simulated failure'; END; $$ LANGUAGE plpgsql");
		jdbcTemplate.execute("CREATE TRIGGER fail_sync_run_insert BEFORE INSERT ON sync_run "
				+ "FOR EACH ROW EXECUTE FUNCTION fail_sync_run_insert()");

		assertThatThrownBy(() -> jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_8,
				stateAfterVersion7.afterSnapshotApplied(8L, NOW), successfulRun(7L, SNAPSHOT_VERSION_8)))
				.isInstanceOf(ReplicaWriteFailedException.class);

		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional", Long.class)).containsExactly(15L);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM weekly_schedule", Long.class)).containsExactly(21L);
		assertThat(jdbcTemplate.queryForObject("SELECT applied_version FROM sync_state", Long.class)).isEqualTo(7L);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sync_run", Long.class)).isEqualTo(1L);

	}

	@Test
	void recordsAFailureWithoutTouchingTheCatalog() {

		SyncState stateAfterVersion7 = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7, stateAfterVersion7,
				successfulRun(null, SNAPSHOT_VERSION_7));
		SyncRun failedRun = SyncRun.builder()
				.triggerSource(SyncTrigger.SCHEDULED)
				.mode(SyncMode.FULL)
				.fromVersion(7L)
				.runResult(SyncRunResult.FAILED)
				.startedAt(NOW)
				.finishedAt(NOW)
				.errorCode("CATALOG_SOURCE_UNAVAILABLE")
				.errorMessage("Could not reach /api/synchronization/snapshot")
				.build();

		jdbcCatalogReplicaWriterAdapter.recordRun(stateAfterVersion7.afterFailure(
				SyncErrorCode.CATALOG_SOURCE_UNAVAILABLE, "Could not reach /api/synchronization/snapshot", NOW),
				failedRun);

		Map<String, Object> syncStateRow = jdbcTemplate.queryForMap("SELECT * FROM sync_state");
		assertThat(syncStateRow.get("applied_version")).isEqualTo(7L);
		assertThat(syncStateRow.get("status")).isEqualTo("DEGRADED");
		assertThat(syncStateRow.get("consecutive_failures")).isEqualTo(1);
		assertThat(syncStateRow.get("last_error_code")).isEqualTo("CATALOG_SOURCE_UNAVAILABLE");
		assertThat(jdbcTemplate.queryForList("SELECT result FROM sync_run ORDER BY id", String.class))
				.containsExactly("SUCCESS", "FAILED");
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional", Long.class)).containsExactly(15L);

	}

	@Test
	void addsAndUpdatesTheChangedEntities() {

		SyncState stateAfterVersion7 = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7, stateAfterVersion7,
				successfulRun(null, SNAPSHOT_VERSION_7));
		// La versión 9 trae una categoría nueva, un profesional nuevo en ella y un cambio de apellido del 15
		CurrentCatalogEntities changedEntities = new CurrentCatalogEntities(
				List.of(new ProfessionalCategory(3L, "Pediatria", null, true)),
				List.of(new Professional(22L, 3L, "Luis", "Perez", true),
						new Professional(15L, 1L, "Ana", "Gómez Paz", true)),
				List.of(), IdsByEntityType.empty());

		jdbcCatalogReplicaWriterAdapter.applyChanges(changedEntities, stateAfterVersion7.afterChangesApplied(9L, NOW),
				incrementalRun(7L, 9L, 3));

		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional ORDER BY id", Long.class))
				.containsExactly(15L, 22L);
		Map<String, Object> updatedProfessionalRow = jdbcTemplate
				.queryForMap("SELECT * FROM professional WHERE id = 15");
		assertThat(updatedProfessionalRow.get("last_name")).isEqualTo("Gómez Paz");
		assertThat(updatedProfessionalRow.get("search_name")).isEqualTo("ana gomez paz");
		// Lo que no cambió sigue igual
		assertThat(jdbcTemplate.queryForList("SELECT id FROM weekly_schedule", Long.class)).containsExactly(21L);
		Map<String, Object> syncStateRow = jdbcTemplate.queryForMap("SELECT * FROM sync_state");
		assertThat(syncStateRow.get("applied_version")).isEqualTo(9L);
		assertThat(syncStateRow.get("remote_version")).isEqualTo(9L);
		assertThat(jdbcTemplate.queryForList("SELECT mode FROM sync_run ORDER BY id", String.class))
				.containsExactly("FULL", "INCREMENTAL");

	}

	@Test
	void keepsTheCopyWhenTheChangesBreakAReference() {

		SyncState stateAfterVersion7 = NEVER_SYNCED_STATE.afterSnapshotApplied(7L, NOW);
		jdbcCatalogReplicaWriterAdapter.replaceAll(SNAPSHOT_VERSION_7, stateAfterVersion7,
				successfulRun(null, SNAPSHOT_VERSION_7));
		// La categoría 3 no está en la copia ni viene en los cambios
		CurrentCatalogEntities changesWithABrokenReference = new CurrentCatalogEntities(
				List.of(new ProfessionalCategory(4L, "Dermatologia", null, true)),
				List.of(new Professional(22L, 3L, "Luis", "Perez", true)), List.of(), IdsByEntityType.empty());

		assertThatThrownBy(() -> jdbcCatalogReplicaWriterAdapter.applyChanges(changesWithABrokenReference,
				stateAfterVersion7.afterChangesApplied(9L, NOW), incrementalRun(7L, 9L, 2)))
				.isInstanceOf(InconsistentChangesException.class);

		// La categoría 4 se insertó antes de fallar y el rollback también la deshizo
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional_category", Long.class)).containsExactly(1L);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM professional", Long.class)).containsExactly(15L);
		assertThat(jdbcTemplate.queryForObject("SELECT applied_version FROM sync_state", Long.class)).isEqualTo(7L);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sync_run", Long.class)).isEqualTo(1L);

	}

	private static SyncRun incrementalRun(long fromVersion, long toVersion, int entitiesApplied) {

		return SyncRun.builder()
				.triggerSource(SyncTrigger.SCHEDULED)
				.mode(SyncMode.INCREMENTAL)
				.fromVersion(fromVersion)
				.toVersion(toVersion)
				.runResult(SyncRunResult.SUCCESS)
				.entitiesApplied(entitiesApplied)
				.startedAt(NOW)
				.finishedAt(NOW)
				.build();

	}

	private static SyncRun successfulRun(Long fromVersion, CatalogSnapshot catalogSnapshot) {

		return SyncRun.builder()
				.triggerSource(SyncTrigger.STARTUP)
				.mode(SyncMode.FULL)
				.fromVersion(fromVersion)
				.toVersion(catalogSnapshot.getSnapshotVersion())
				.runResult(SyncRunResult.SUCCESS)
				.entitiesApplied(catalogSnapshot.getProfessionalCategories().size()
						+ catalogSnapshot.getProfessionals().size() + catalogSnapshot.getWeeklySchedules().size())
				.startedAt(NOW)
				.finishedAt(NOW)
				.build();

	}
}
