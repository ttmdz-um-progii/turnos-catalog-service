package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.InconsistentChangesException;
import ar.edu.um.progii.catalog.sync.application.exception.ReplicaWriteFailedException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogReplicaWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Escribe la réplica, sync_state y sync_run con SQL (regla 18b, decisión 18): DELETE para el snapshot e INSERT ... ON
// CONFLICT para agregar o actualizar. Cada método es una sola transacción (decisión 22): si una sentencia falla, el
// rollback deshace las anteriores y la copia queda como estaba. Mientras dura, las búsquedas siguen viendo el catálogo
// anterior completo, porque nada se ve hasta el commit
@Component
@RequiredArgsConstructor
public class JdbcCatalogReplicaWriterAdapter implements CatalogReplicaWriter {

	// sync_state tiene una sola fila, que inserta la migración V3
	private static final short SYNC_STATE_ID = 1;

	private final JdbcTemplate jdbcTemplate;

	@Override
	@Transactional
	public void replaceAll(CatalogSnapshot catalogSnapshot, SyncState stateAfterSnapshot, SyncRun successfulRun) {

		try {
			// Hijos antes que padres, para no violar las claves foráneas
			jdbcTemplate.update("DELETE FROM weekly_schedule");
			jdbcTemplate.update("DELETE FROM professional");
			jdbcTemplate.update("DELETE FROM professional_category");

			// Padres antes que hijos
			upsertEntities(catalogSnapshot.getProfessionalCategories(), catalogSnapshot.getProfessionals(),
					catalogSnapshot.getWeeklySchedules());

			updateSyncState(stateAfterSnapshot);
			insertSyncRun(successfulRun);
		} catch (DataAccessException databaseException) {
			throw new ReplicaWriteFailedException(
					"Could not replace the catalog with snapshot " + catalogSnapshot.getSnapshotVersion(),
					databaseException);
		}

	}

	@Override
	@Transactional
	public void applyChanges(CurrentCatalogEntities currentCatalogEntities, SyncState stateAfterChanges,
			SyncRun successfulRun) {

		try {
			upsertEntities(currentCatalogEntities.getProfessionalCategories(),
					currentCatalogEntities.getProfessionals(), currentCatalogEntities.getWeeklySchedules());
			updateSyncState(stateAfterChanges);
			insertSyncRun(successfulRun);
		} catch (DataIntegrityViolationException brokenReferenceException) {
			// Por ejemplo, un profesional que apunta a una categoría que la copia no tiene
			throw new InconsistentChangesException(
					"Applying the changes up to version " + successfulRun.getToVersion() + " breaks a reference",
					brokenReferenceException);
		} catch (DataAccessException databaseException) {
			throw new ReplicaWriteFailedException(
					"Could not apply the changes up to version " + successfulRun.getToVersion(), databaseException);
		}

	}

	@Override
	@Transactional
	public void recordRun(SyncState stateAfterRun, SyncRun recordedRun) {

		try {
			updateSyncState(stateAfterRun);
			insertSyncRun(recordedRun);
		} catch (DataAccessException databaseException) {
			throw new ReplicaWriteFailedException("Could not record the " + recordedRun.getRunResult()
					+ " synchronization", databaseException);
		}

	}

	// Inserta cada entidad o, si su id ya existe, la reemplaza: aplicar dos veces lo mismo no cambia nada. Va en orden
	// de padres a hijos, para que cada referencia apunte a algo que ya está
	private void upsertEntities(List<ProfessionalCategory> professionalCategories, List<Professional> professionals,
			List<WeeklySchedule> weeklySchedules) {

		for (ProfessionalCategory professionalCategory : professionalCategories) {
			jdbcTemplate.update(
					"INSERT INTO professional_category (id, name, description, enabled) VALUES (?, ?, ?, ?) "
							+ "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, "
							+ "enabled = EXCLUDED.enabled",
					professionalCategory.getId(), professionalCategory.getName(),
					professionalCategory.getDescription(), professionalCategory.isEnabled());
		}
		for (Professional professional : professionals) {
			jdbcTemplate.update(
					"INSERT INTO professional (id, category_id, first_name, last_name, search_name, enabled) "
							+ "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET category_id = EXCLUDED.category_id, "
							+ "first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, "
							+ "search_name = EXCLUDED.search_name, enabled = EXCLUDED.enabled",
					professional.getId(),
					professional.getCategoryId(), professional.getFirstName(), professional.getLastName(),
					professional.getSearchName(), professional.isEnabled());
		}
		for (WeeklySchedule weeklySchedule : weeklySchedules) {
			jdbcTemplate.update("INSERT INTO weekly_schedule (id, professional_id, day_of_week, start_time, end_time, "
					+ "slot_duration_minutes, enabled) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
					+ "professional_id = EXCLUDED.professional_id, day_of_week = EXCLUDED.day_of_week, "
					+ "start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time, "
					+ "slot_duration_minutes = EXCLUDED.slot_duration_minutes, enabled = EXCLUDED.enabled",
					weeklySchedule.getId(), weeklySchedule.getProfessionalId(), weeklySchedule.getDayOfWeek().name(),
					weeklySchedule.getStartTime(), weeklySchedule.getEndTime(),
					weeklySchedule.getSlotDurationMinutes(), weeklySchedule.isEnabled());
		}

	}

	private void updateSyncState(SyncState syncState) {

		jdbcTemplate.update("UPDATE sync_state SET applied_version = ?, remote_version = ?, status = ?, "
				+ "last_success_at = ?, last_attempt_at = ?, last_full_sync_at = ?, consecutive_failures = ?, "
				+ "last_error_code = ?, last_error_message = ? WHERE id = ?", syncState.getAppliedVersion(),
				syncState.getRemoteVersion(), syncState.getStatus().name(), toTimestamp(syncState.getLastSuccessAt()),
				toTimestamp(syncState.getLastAttemptAt()), toTimestamp(syncState.getLastFullSyncAt()),
				syncState.getConsecutiveFailures(), syncState.getLastErrorCode(), syncState.getLastErrorMessage(),
				SYNC_STATE_ID);

	}

	// El id lo genera la base (GENERATED ALWAYS AS IDENTITY)
	private void insertSyncRun(SyncRun syncRun) {

		jdbcTemplate.update("INSERT INTO sync_run (trigger_source, event_id, mode, from_version, to_version, result, "
				+ "entities_applied, started_at, finished_at, error_code, error_message) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", syncRun.getTriggerSource().name(), syncRun.getEventId(),
				syncRun.getMode().name(), syncRun.getFromVersion(), syncRun.getToVersion(),
				syncRun.getRunResult().name(), syncRun.getEntitiesApplied(), toTimestamp(syncRun.getStartedAt()),
				toTimestamp(syncRun.getFinishedAt()), syncRun.getErrorCode(), syncRun.getErrorMessage());

	}

	// El driver de PostgreSQL no recibe Instant directo: Timestamp es el mismo instante, en el tipo que entiende
	private static Timestamp toTimestamp(Instant instant) {

		return instant == null ? null : Timestamp.from(instant);

	}
}
