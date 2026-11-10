package ar.edu.um.progii.catalog.sync.infrastructure.redis.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Valor de un campo del Hash catedra:sync:weekly-schedules (anexo §14.4). Las horas llegan como HH:mm o HH:mm:ss,
// y LocalTime acepta las dos
public record WeeklyScheduleRedisDto(long id, long professionalId, DayOfWeek dayOfWeek, LocalTime startTime,
		LocalTime endTime, int slotDurationMinutes, boolean enabled) {
}
