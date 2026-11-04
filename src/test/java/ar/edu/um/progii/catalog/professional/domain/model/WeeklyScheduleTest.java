package ar.edu.um.progii.catalog.professional.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class WeeklyScheduleTest {

	private static final LocalTime NINE_AM = LocalTime.of(9, 0);
	private static final LocalTime NOON = LocalTime.of(12, 0);

	@Test
	void rejectsNullDayOfWeek() {

		assertThatThrownBy(() -> new WeeklySchedule(21, 15, null, NINE_AM, NOON, 30, true))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullStartTime() {

		assertThatThrownBy(() -> new WeeklySchedule(21, 15, DayOfWeek.MONDAY, null, NOON, 30, true))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullEndTime() {

		assertThatThrownBy(() -> new WeeklySchedule(21, 15, DayOfWeek.MONDAY, NINE_AM, null, 30, true))
				.isInstanceOf(IllegalArgumentException.class);

	}

	// No se es más estricto que la cátedra: si publica una franja así, la réplica la acepta
	@Test
	void acceptsAStartTimeAfterTheEndTime() {

		assertThatCode(() -> new WeeklySchedule(21, 15, DayOfWeek.MONDAY, NOON, NINE_AM, 30, true))
				.doesNotThrowAnyException();

	}
}
