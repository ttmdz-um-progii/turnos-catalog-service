package ar.edu.um.progii.catalog.sync.domain.model;

// Cómo se sincronizó la réplica en una ejecución
public enum SyncMode {
	// Se reemplazó la réplica entera por el snapshot de la cátedra
	FULL,
	// Se aplicaron los cambios publicados entre la versión local y la actual
	INCREMENTAL
}
