package ar.edu.um.progii.catalog.professional.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.professional.infrastructure.persistence.entity.ProfessionalEntity;

public interface JpaProfessionalRepository extends JpaRepository<ProfessionalEntity, Long> {
}
