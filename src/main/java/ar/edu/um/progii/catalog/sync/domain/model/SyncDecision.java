package ar.edu.um.progii.catalog.sync.domain.model;

import lombok.Getter;

// Qué hacer en una sincronización y, si es incremental, qué ids aplicar por tipo de entidad.
// Solo se crea con noop(), snapshot() o incremental(idsToApply) (regla 13, variantes cerradas): así NOOP y SNAPSHOT
// nunca llevan ids
@Getter
public class SyncDecision {

	private final SyncAction action;
	private final IdsByEntityType idsToApply;

	private SyncDecision(SyncAction action, IdsByEntityType idsToApply) {

		this.action = action;
		this.idsToApply = validateIdsToApply(idsToApply);

	}

	public static SyncDecision noop() {

		return new SyncDecision(SyncAction.NOOP, IdsByEntityType.empty());

	}

	public static SyncDecision snapshot() {

		return new SyncDecision(SyncAction.SNAPSHOT, IdsByEntityType.empty());

	}

	// Puede venir vacío: el tramo tuvo versiones sin cambios y solo hay que avanzar la versión local
	public static SyncDecision incremental(IdsByEntityType idsToApply) {

		return new SyncDecision(SyncAction.INCREMENTAL, idsToApply);

	}

	private static IdsByEntityType validateIdsToApply(IdsByEntityType idsToApply) {

		if (idsToApply == null) {
			throw new IllegalArgumentException("Ids to apply are mandatory");
		}
		return idsToApply;

	}
}
