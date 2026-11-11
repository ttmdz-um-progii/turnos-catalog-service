package ar.edu.um.progii.catalog.sync.domain.model;

// Por qué falló una ejecución de la sincronización. Se registra en el estado y en la ejecución para decidir por el
// código, nunca por el texto del mensaje
public enum SyncErrorCode {
	// La cátedra no respondió a tiempo, aun después de reintentar
	CATALOG_SOURCE_UNAVAILABLE,
	// La cátedra rechazó las credenciales técnicas del servicio: reintentar no lo arregla
	CATALOG_CREDENTIALS_REJECTED,
	// Lo que publicó la cátedra no se puede leer o no es consistente
	INVALID_CATALOG_SNAPSHOT,
	// La copia local no se pudo escribir y quedó como estaba
	REPLICA_WRITE_FAILED
}
