package ar.edu.um.progii.catalog.sync.domain.model;

// Estado visible de la réplica del catálogo
public enum SyncStatus {
	// Nunca se sincronizó: no hay datos
	EMPTY,
	// La última ejecución terminó bien y la versión local alcanzó la actual de la cátedra
	IN_SYNC,
	// Hay datos válidos, pero la última ejecución falló o se sabe que hay versiones más nuevas
	DEGRADED,
	// Falla que no se resuelve sola, como credenciales inválidas
	ERROR
}
