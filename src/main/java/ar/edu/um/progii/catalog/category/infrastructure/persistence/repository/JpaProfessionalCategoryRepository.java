package ar.edu.um.progii.catalog.category.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.category.infrastructure.persistence.entity.ProfessionalCategoryEntity;

public interface JpaProfessionalCategoryRepository extends JpaRepository<ProfessionalCategoryEntity, Long> {
}
