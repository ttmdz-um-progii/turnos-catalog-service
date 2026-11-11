package ar.edu.um.progii.catalog.sync.infrastructure.rest.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Elemento de weeklySchedules en el snapshot (anexo §7). Las horas llegan como HH:mm o HH:mm:ss, y LocalTime acepta
// las dos
public record WeeklyScheduleApiResponse(long id, long professionalId, DayOfWeek dayOfWeek, LocalTime startTime,
		LocalTime endTime, int slotDurationMinutes, boolean enabled) {
}
