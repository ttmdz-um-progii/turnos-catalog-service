package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SyncStateTest {

	private static final Instant PREVIOUS_ATTEMPT_AT = Instant.parse("2026-09-10T13:00:00Z");
	private static final Instant NOW = Instant.parse("2026-09-10T14:00:00Z");

	private static final SyncState NEVER_SYNCED_STATE = new SyncState(null, null, SyncStatus.EMPTY, null, null, null,
			0, null, null);
	// Tiene la versión 5 aplicada y viene de dos fallas
	private static final SyncState FAILING_STATE_WITH_DATA = new SyncState(5L, 5L, SyncStatus.DEGRADED,
			PREVIOUS_ATTEMPT_AT, PREVIOUS_ATTEMPT_AT, PREVIOUS_ATTEMPT_AT, 2, "CATALOG_SOURCE_UNAVAILABLE", "Timeout");

	// El estado con el que arranca la réplica: nunca sincronizada y sin fechas ni errores
	@Test
	void acceptsTheNeverSyncedState() {

		assertThatCode(() -> new SyncState(null, null, SyncStatus.EMPTY, null, null, null, 0, null, null))
				.doesNotThrowAnyException();

	}

	@Test
	void rejectsNullStatus() {

		assertThatThrownBy(() -> new SyncState(null, null, null, null, null, null, 0, null, null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNegativeConsecutiveFailures() {

		assertThatThrownBy(() -> new SyncState(null, null, SyncStatus.EMPTY, null, null, null, -1, null, null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void appliesTheSnapshotAndClearsTheFailures() {

		SyncState stateAfterSnapshot = FAILING_STATE_WITH_DATA.afterSnapshotApplied(7L, NOW);

		assertThat(stateAfterSnapshot.getAppliedVersion()).isEqualTo(7L);
		assertThat(stateAfterSnapshot.getStatus()).isEqualTo(SyncStatus.IN_SYNC);
		assertThat(stateAfterSnapshot.getLastSuccessAt()).isEqualTo(NOW);
		assertThat(stateAfterSnapshot.getLastAttemptAt()).isEqualTo(NOW);
		assertThat(stateAfterSnapshot.getLastFullSyncAt()).isEqualTo(NOW);
		assertThat(stateAfterSnapshot.getConsecutiveFailures()).isZero();
		assertThat(stateAfterSnapshot.getLastErrorCode()).isNull();
		assertThat(stateAfterSnapshot.getLastErrorMessage()).isNull();

	}

	// La cátedra ya había publicado la 9 y el snapshot trajo la 7: la copia sigue atrasada
	@Test
	void staysDegradedWhenANewerVersionIsKnown() {

		SyncState stateWithNewerRemoteVersion = new SyncState(5L, 9L, SyncStatus.DEGRADED, null, null, null, 0, null,
				null);

		SyncState stateAfterSnapshot = stateWithNewerRemoteVersion.afterSnapshotApplied(7L, NOW);

		assertThat(stateAfterSnapshot.getStatus()).isEqualTo(SyncStatus.DEGRADED);
		assertThat(stateAfterSnapshot.getRemoteVersion()).isEqualTo(9L);

	}

	@Test
	void goesToErrorWhenTheCredentialsAreRejected() {

		SyncState stateAfterFailure = FAILING_STATE_WITH_DATA.afterFailure(SyncErrorCode.CATALOG_CREDENTIALS_REJECTED,
				"401", NOW);

		assertThat(stateAfterFailure.getStatus()).isEqualTo(SyncStatus.ERROR);
		assertThat(stateAfterFailure.getConsecutiveFailures()).isEqualTo(3);
		assertThat(stateAfterFailure.getLastErrorCode()).isEqualTo("CATALOG_CREDENTIALS_REJECTED");
		assertThat(stateAfterFailure.getLastErrorMessage()).isEqualTo("401");
		assertThat(stateAfterFailure.getLastAttemptAt()).isEqualTo(NOW);

	}

	@Test
	void keepsTheCopyAndGoesDegradedWhenThereIsData() {

		SyncState stateAfterFailure = FAILING_STATE_WITH_DATA.afterFailure(SyncErrorCode.CATALOG_SOURCE_UNAVAILABLE,
				"503", NOW);

		assertThat(stateAfterFailure.getStatus()).isEqualTo(SyncStatus.DEGRADED);
		assertThat(stateAfterFailure.getAppliedVersion()).isEqualTo(5L);
		assertThat(stateAfterFailure.getLastSuccessAt()).isEqualTo(PREVIOUS_ATTEMPT_AT);
		assertThat(stateAfterFailure.getLastFullSyncAt()).isEqualTo(PREVIOUS_ATTEMPT_AT);

	}

	@Test
	void staysEmptyWhenItNeverHadData() {

		SyncState stateAfterFailure = NEVER_SYNCED_STATE.afterFailure(SyncErrorCode.INVALID_CATALOG_SNAPSHOT,
				"Duplicate professional id 15", NOW);

		assertThat(stateAfterFailure.getStatus()).isEqualTo(SyncStatus.EMPTY);
		assertThat(stateAfterFailure.getConsecutiveFailures()).isEqualTo(1);

	}

	@Test
	void rejectsAFailureWithoutErrorCode() {

		assertThatThrownBy(() -> NEVER_SYNCED_STATE.afterFailure(null, "Timeout", NOW))
				.isInstanceOf(IllegalArgumentException.class);

	}
}
