package ar.edu.um.progii.catalog.sync.domain.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

// Una ejecución de la sincronización: qué la disparó, cómo sincronizó, entre qué versiones y cómo terminó
@Getter
public class SyncRun {

	// Null hasta que se guarda: lo genera el registro de ejecuciones
	private final Long id;
	private final SyncTrigger triggerSource;
	// Id del aviso de la cátedra que la disparó. Null si no la disparó un aviso
	private final String eventId;
	private final SyncMode mode;
	// Null si la ejecución terminó antes de conocer las versiones
	private final Long fromVersion;
	private final Long toVersion;
	private final SyncRunResult runResult;
	private final int entitiesApplied;
	private final Instant startedAt;
	private final Instant finishedAt;
	private final String errorCode;
	private final String errorMessage;

	@Builder
	public SyncRun(Long id, SyncTrigger triggerSource, String eventId, SyncMode mode, Long fromVersion, Long toVersion,
			SyncRunResult runResult, int entitiesApplied, Instant startedAt, Instant finishedAt, String errorCode,
			String errorMessage) {

		this.id = id;
		this.triggerSource = validateTriggerSource(triggerSource);
		this.eventId = eventId;
		this.mode = validateMode(mode);
		this.fromVersion = fromVersion;
		this.toVersion = toVersion;
		this.runResult = validateRunResult(runResult);
		this.entitiesApplied = validateEntitiesApplied(entitiesApplied);
		this.startedAt = validateStartedAt(startedAt);
		this.finishedAt = validateFinishedAt(finishedAt);
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;

	}

	private static SyncTrigger validateTriggerSource(SyncTrigger triggerSource) {

		if (triggerSource == null) {
			throw new IllegalArgumentException("Trigger source is mandatory");
		}
		return triggerSource;

	}

	private static SyncMode validateMode(SyncMode mode) {

		if (mode == null) {
			throw new IllegalArgumentException("Mode is mandatory");
		}
		return mode;

	}

	private static SyncRunResult validateRunResult(SyncRunResult runResult) {

		if (runResult == null) {
			throw new IllegalArgumentException("Run result is mandatory");
		}
		return runResult;

	}

	private static int validateEntitiesApplied(int entitiesApplied) {

		if (entitiesApplied < 0) {
			throw new IllegalArgumentException("Entities applied cannot be negative");
		}
		return entitiesApplied;

	}

	private static Instant validateStartedAt(Instant startedAt) {

		if (startedAt == null) {
			throw new IllegalArgumentException("Started at is mandatory");
		}
		return startedAt;

	}

	private static Instant validateFinishedAt(Instant finishedAt) {

		if (finishedAt == null) {
			throw new IllegalArgumentException("Finished at is mandatory");
		}
		return finishedAt;

	}
}
