package ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.CatalogSyncStateEntity;

public interface JpaCatalogSyncStateRepository extends JpaRepository<CatalogSyncStateEntity, Short> {
}
