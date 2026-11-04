package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SyncRunTest {

	private static final Instant STARTED_AT = Instant.parse("2026-09-10T13:44:59Z");
	private static final Instant FINISHED_AT = Instant.parse("2026-09-10T13:45:01Z");

	// Una ejecución sin guardar, que no vino de un aviso y falló antes de conocer las versiones
	@Test
	void acceptsNullOptionalFields() {

		assertThatCode(() -> new SyncRun(null, SyncTrigger.SCHEDULED, null, SyncMode.INCREMENTAL, null, null,
				SyncRunResult.FAILED, 0, STARTED_AT, FINISHED_AT, null, null)).doesNotThrowAnyException();

	}

	@Test
	void rejectsNullTriggerSource() {

		assertThatThrownBy(() -> new SyncRun(null, null, null, SyncMode.INCREMENTAL, 6L, 9L, SyncRunResult.SUCCESS, 3,
				STARTED_AT, FINISHED_AT, null, null)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullMode() {

		assertThatThrownBy(() -> new SyncRun(null, SyncTrigger.STARTUP, null, null, 6L, 9L, SyncRunResult.SUCCESS, 3,
				STARTED_AT, FINISHED_AT, null, null)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullRunResult() {

		assertThatThrownBy(() -> new SyncRun(null, SyncTrigger.STARTUP, null, SyncMode.INCREMENTAL, 6L, 9L, null, 3,
				STARTED_AT, FINISHED_AT, null, null)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNegativeEntitiesApplied() {

		assertThatThrownBy(() -> new SyncRun(null, SyncTrigger.STARTUP, null, SyncMode.INCREMENTAL, 6L, 9L,
				SyncRunResult.SUCCESS, -1, STARTED_AT, FINISHED_AT, null, null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullStartedAt() {

		assertThatThrownBy(() -> new SyncRun(null, SyncTrigger.STARTUP, null, SyncMode.INCREMENTAL, 6L, 9L,
				SyncRunResult.SUCCESS, 3, null, FINISHED_AT, null, null)).isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void rejectsNullFinishedAt() {

		assertThatThrownBy(() -> new SyncRun(null, SyncTrigger.STARTUP, null, SyncMode.INCREMENTAL, 6L, 9L,
				SyncRunResult.SUCCESS, 3, STARTED_AT, null, null, null)).isInstanceOf(IllegalArgumentException.class);

	}
}
