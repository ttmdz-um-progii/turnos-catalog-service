package ar.edu.um.progii.catalog.sync.infrastructure.persistence.mapper;

import ar.edu.um.progii.catalog.sync.domain.model.SyncState;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity.SyncStateEntity;
import org.springframework.stereotype.Component;

// Solo de la base al modelo: sync_state lo escribe JdbcCatalogReplicaWriterAdapter (decisión 22)
@Component
public class SyncStateMapper {

	public SyncState toDomainModel(SyncStateEntity syncStateEntity) {

		if (syncStateEntity == null) {
			return null;
		}
		return SyncState.builder()
				.appliedVersion(syncStateEntity.getAppliedVersion())
				.remoteVersion(syncStateEntity.getRemoteVersion())
				.status(syncStateEntity.getStatus())
				.lastSuccessAt(syncStateEntity.getLastSuccessAt())
				.lastAttemptAt(syncStateEntity.getLastAttemptAt())
				.lastFullSyncAt(syncStateEntity.getLastFullSyncAt())
				.consecutiveFailures(syncStateEntity.getConsecutiveFailures())
				.lastErrorCode(syncStateEntity.getLastErrorCode())
				.lastErrorMessage(syncStateEntity.getLastErrorMessage())
				.build();

	}
}
