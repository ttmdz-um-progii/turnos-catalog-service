package ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "weekly_schedule")
@NoArgsConstructor
public class WeeklyScheduleEntity {

	// Id de la cátedra: sin @GeneratedValue
	@Id
	private Long id;
	private Long professionalId;
	@Enumerated(EnumType.STRING)
	private DayOfWeek dayOfWeek;
	private LocalTime startTime;
	private LocalTime endTime;
	private int slotDurationMinutes;
	private boolean enabled;
}
