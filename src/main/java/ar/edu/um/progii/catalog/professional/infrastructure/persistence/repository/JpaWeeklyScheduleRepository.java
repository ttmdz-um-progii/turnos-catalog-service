package ar.edu.um.progii.catalog.professional.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity.WeeklyScheduleEntity;

public interface JpaWeeklyScheduleRepository extends JpaRepository<WeeklyScheduleEntity, Long> {
}
