package ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.SyncRunEntity;

public interface JpaSyncRunRepository extends JpaRepository<SyncRunEntity, Long> {

	// Spring Data arma la consulta con el nombre: event_id = ? AND result IN (...). La resuelve el índice sobre
	// event_id
	boolean existsByEventIdAndRunResultIn(String eventId, Collection<SyncRunResult> runResults);
}
