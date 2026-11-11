package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncStateRepository;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.mapper.SyncStateMapper;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository.JpaSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaSyncStateRepositoryAdapter implements SyncStateRepository {

	// sync_state tiene una sola fila, que inserta la migración V3
	private static final short SYNC_STATE_ID = 1;

	private final JpaSyncStateRepository jpaSyncStateRepository;
	private final SyncStateMapper syncStateMapper;

	@Override
	@Transactional(readOnly = true)
	public SyncState findCurrent() {

		return jpaSyncStateRepository.findById(SYNC_STATE_ID)
				.map(syncStateMapper::toDomainModel)
				.orElseThrow(() -> new IllegalStateException("The sync_state row is missing: check the migrations"));

	}
}
