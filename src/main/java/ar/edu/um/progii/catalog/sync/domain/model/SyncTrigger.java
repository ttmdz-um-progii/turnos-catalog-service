package ar.edu.um.progii.catalog.sync.domain.model;

// Qué disparó una ejecución de la sincronización
public enum SyncTrigger {
	// El arranque del servicio
	STARTUP,
	// Un aviso de la cátedra de que publicó una versión nueva del catálogo
	NOTIFICATION,
	// La reconciliación periódica
	SCHEDULED,
	// Un pedido manual de resincronización
	MANUAL
}
