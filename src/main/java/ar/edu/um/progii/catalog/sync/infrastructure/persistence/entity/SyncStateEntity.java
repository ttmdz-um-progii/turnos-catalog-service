package ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity;

import java.time.Instant;

import ar.edu.um.progii.catalog.sync.domain.model.SyncStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "sync_state")
@NoArgsConstructor
public class SyncStateEntity {

	// Siempre 1: la tabla tiene una sola fila, que inserta la migración
	@Id
	private Short id;
	private Long appliedVersion;
	private Long remoteVersion;
	@Enumerated(EnumType.STRING)
	private SyncStatus status;
	private Instant lastSuccessAt;
	private Instant lastAttemptAt;
	private Instant lastFullSyncAt;
	private int consecutiveFailures;
	private String lastErrorCode;
	private String lastErrorMessage;
}
