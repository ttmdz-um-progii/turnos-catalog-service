package ar.edu.um.progii.catalog.professional.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Un horario semanal de atención de un profesional: un día, una franja y la duración de cada turno
public class WeeklySchedule {

	// Id que asigna la cátedra
	private final long id;
	private final long professionalId;
	private final DayOfWeek dayOfWeek;
	// Sin validar que startTime sea anterior a endTime: no se es más estricto que la cátedra
	private final LocalTime startTime;
	private final LocalTime endTime;
	private final int slotDurationMinutes;
	private final boolean enabled;

	public WeeklySchedule(long id, long professionalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
			int slotDurationMinutes, boolean enabled) {

		this.id = id;
		this.professionalId = professionalId;
		this.dayOfWeek = validateDayOfWeek(dayOfWeek);
		this.startTime = validateStartTime(startTime);
		this.endTime = validateEndTime(endTime);
		this.slotDurationMinutes = slotDurationMinutes;
		this.enabled = enabled;

	}

	private static DayOfWeek validateDayOfWeek(DayOfWeek dayOfWeek) {

		if (dayOfWeek == null) {
			throw new IllegalArgumentException("Day of week is mandatory");
		}
		return dayOfWeek;

	}

	private static LocalTime validateStartTime(LocalTime startTime) {

		if (startTime == null) {
			throw new IllegalArgumentException("Start time is mandatory");
		}
		return startTime;

	}

	private static LocalTime validateEndTime(LocalTime endTime) {

		if (endTime == null) {
			throw new IllegalArgumentException("End time is mandatory");
		}
		return endTime;

	}
}
