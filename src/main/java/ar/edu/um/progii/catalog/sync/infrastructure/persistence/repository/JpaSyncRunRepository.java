package ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.SyncRunEntity;

public interface JpaSyncRunRepository extends JpaRepository<SyncRunEntity, Long> {
}
