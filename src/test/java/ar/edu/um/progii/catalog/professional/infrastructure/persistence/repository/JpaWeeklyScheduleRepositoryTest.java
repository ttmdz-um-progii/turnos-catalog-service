package ar.edu.um.progii.catalog.professional.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity.WeeklyScheduleEntity;

// Cada test hace rollback al terminar (@Transactional), así no deja filas para los demás
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaWeeklyScheduleRepositoryTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaWeeklyScheduleRepository jpaWeeklyScheduleRepository;

	@Test
	void readsAScheduleWrittenWithSql() {

		jdbcTemplate.update("INSERT INTO professional_category (id, name, enabled) VALUES (?, ?, ?)", 1L,
				"Clínica médica", true);
		jdbcTemplate.update(
				"INSERT INTO professional (id, category_id, first_name, last_name, search_name, enabled) VALUES (?, ?, ?, ?, ?, ?)",
				15L, 1L, "Ana", "Gómez", "ana gomez", true);
		jdbcTemplate.update(
				"INSERT INTO weekly_schedule (id, professional_id, day_of_week, start_time, end_time, slot_duration_minutes, enabled) VALUES (?, ?, ?, ?::time, ?::time, ?, ?)",
				21L, 15L, "MONDAY", "09:00:00", "12:00:00", 30, true);

		WeeklyScheduleEntity schedule = jpaWeeklyScheduleRepository.findById(21L).orElseThrow();

		assertThat(schedule.getId()).isEqualTo(21L);
		assertThat(schedule.getProfessionalId()).isEqualTo(15L);
		assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(schedule.getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(schedule.getSlotDurationMinutes()).isEqualTo(30);
		assertThat(schedule.isEnabled()).isTrue();

	}
}
