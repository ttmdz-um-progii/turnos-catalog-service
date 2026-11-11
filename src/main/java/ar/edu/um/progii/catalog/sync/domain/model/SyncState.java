package ar.edu.um.progii.catalog.sync.domain.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

// Estado actual de la réplica del catálogo: qué versión tiene aplicada, cómo está y cómo le fue a la última ejecución.
// Es inmutable: cada cambio de estado devuelve uno nuevo
@Getter
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

	@Builder
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

	// El estado después de reemplazar la copia por el snapshot: queda al día salvo que ya se sepa de una versión más
	// nueva en la cátedra, y la racha de fallas se corta
	public SyncState afterSnapshotApplied(long snapshotVersion, Instant appliedAt) {

		boolean newerVersionKnown = remoteVersion != null && remoteVersion > snapshotVersion;
		SyncStatus statusAfterSnapshot = newerVersionKnown ? SyncStatus.DEGRADED : SyncStatus.IN_SYNC;
		return new SyncState(snapshotVersion, remoteVersion, statusAfterSnapshot, appliedAt, appliedAt, appliedAt, 0,
				null, null);

	}

	// El estado después de aplicar los cambios hasta currentVersion: la copia alcanzó la última versión publicada y la
	// racha de fallas se corta. lastFullSyncAt no cambia, porque no se reemplazó la copia entera
	public SyncState afterChangesApplied(long currentVersion, Instant appliedAt) {

		return new SyncState(currentVersion, currentVersion, SyncStatus.IN_SYNC, appliedAt, appliedAt, lastFullSyncAt,
				0, null, null);

	}

	// El estado después de una ejecución sin nada que aplicar: la copia ya tenía currentVersion o una posterior
	public SyncState afterNoop(long currentVersion, Instant checkedAt) {

		return new SyncState(appliedVersion, currentVersion, SyncStatus.IN_SYNC, checkedAt, checkedAt, lastFullSyncAt,
				0, null, null);

	}

	// El estado después de una ejecución fallida: la copia y su versión no cambian. Credenciales rechazadas es ERROR
	// porque no se resuelve sola; cualquier otra falla deja EMPTY si nunca hubo datos y DEGRADED si los hay
	public SyncState afterFailure(SyncErrorCode errorCode, String errorMessage, Instant attemptedAt) {

		SyncStatus statusAfterFailure;
		if (validateErrorCode(errorCode) == SyncErrorCode.CATALOG_CREDENTIALS_REJECTED) {
			statusAfterFailure = SyncStatus.ERROR;
		} else if (appliedVersion == null) {
			statusAfterFailure = SyncStatus.EMPTY;
		} else {
			statusAfterFailure = SyncStatus.DEGRADED;
		}
		return new SyncState(appliedVersion, remoteVersion, statusAfterFailure, lastSuccessAt, attemptedAt,
				lastFullSyncAt, consecutiveFailures + 1, errorCode.name(), errorMessage);

	}

	private static SyncErrorCode validateErrorCode(SyncErrorCode errorCode) {

		if (errorCode == null) {
			throw new IllegalArgumentException("Error code is mandatory");
		}
		return errorCode;

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
