package ar.edu.um.progii.catalog.sync.domain.model;

import java.time.Instant;

// Estado actual de la réplica del catálogo: qué versión tiene aplicada, cómo está y cómo le fue a la última ejecución
public class SyncState {

	// Versión de la cátedra que tiene aplicada la réplica. Null = nunca sincronizado
	private final Long appliedVersion;
	// Última versión actual de la cátedra que se leyó. Null = todavía no se leyó ninguna
	private final Long remoteVersion;
	private final SyncStatus status;
	private final Instant lastSuccessAt;
	private final Instant lastAttemptAt;
	private final Instant lastFullSyncAt;
	private final int consecutiveFailures;
	private final String lastErrorCode;
	private final String lastErrorMessage;

	public SyncState(Long appliedVersion, Long remoteVersion, SyncStatus status, Instant lastSuccessAt,
			Instant lastAttemptAt, Instant lastFullSyncAt, int consecutiveFailures, String lastErrorCode,
			String lastErrorMessage) {

		this.appliedVersion = appliedVersion;
		this.remoteVersion = remoteVersion;
		this.status = validateStatus(status);
		this.lastSuccessAt = lastSuccessAt;
		this.lastAttemptAt = lastAttemptAt;
		this.lastFullSyncAt = lastFullSyncAt;
		this.consecutiveFailures = validateConsecutiveFailures(consecutiveFailures);
		this.lastErrorCode = lastErrorCode;
		this.lastErrorMessage = lastErrorMessage;

	}

	private static SyncStatus validateStatus(SyncStatus status) {

		if (status == null) {
			throw new IllegalArgumentException("Status is mandatory");
		}
		return status;

	}

	private static int validateConsecutiveFailures(int consecutiveFailures) {

		if (consecutiveFailures < 0) {
			throw new IllegalArgumentException("Consecutive failures cannot be negative");
		}
		return consecutiveFailures;

	}
}
