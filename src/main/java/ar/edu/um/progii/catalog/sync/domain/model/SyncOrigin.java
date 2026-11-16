package ar.edu.um.progii.catalog.sync.domain.model;

import lombok.Getter;

// Qué disparó una sincronización y, si fue un aviso de la cátedra, su id. Cada ejecución que registra la sincronización
// lleva los dos. Solo se crea con startup(), scheduled(), manual() o notification(eventId) (regla 13, variantes
// cerradas): así solo un aviso lleva eventId, y siempre lo lleva
@Getter
public class SyncOrigin {

	private final SyncTrigger trigger;
	private final String eventId;

	private SyncOrigin(SyncTrigger trigger, String eventId) {

		this.trigger = validateTrigger(trigger);
		this.eventId = validateEventId(trigger, eventId);

	}

	public static SyncOrigin startup() {

		return new SyncOrigin(SyncTrigger.STARTUP, null);

	}

	public static SyncOrigin scheduled() {

		return new SyncOrigin(SyncTrigger.SCHEDULED, null);

	}

	public static SyncOrigin manual() {

		return new SyncOrigin(SyncTrigger.MANUAL, null);

	}

	public static SyncOrigin notification(String eventId) {

		return new SyncOrigin(SyncTrigger.NOTIFICATION, eventId);

	}

	private static SyncTrigger validateTrigger(SyncTrigger trigger) {

		if (trigger == null) {
			throw new IllegalArgumentException("Trigger is mandatory");
		}
		return trigger;

	}

	// El constructor sigue validando: es la red de seguridad si alguien agrega una fábrica mal armada
	private static String validateEventId(SyncTrigger trigger, String eventId) {

		boolean isNotification = trigger == SyncTrigger.NOTIFICATION;
		if (isNotification && (eventId == null || eventId.isBlank())) {
			throw new IllegalArgumentException("A notification needs an event id");
		}
		if (!isNotification && eventId != null) {
			throw new IllegalArgumentException("Only a notification carries an event id");
		}
		return eventId;

	}
}
