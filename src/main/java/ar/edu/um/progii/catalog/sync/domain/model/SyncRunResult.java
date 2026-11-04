package ar.edu.um.progii.catalog.sync.domain.model;

// Cómo terminó una ejecución de la sincronización
public enum SyncRunResult {
	// Se aplicaron los cambios y la versión local avanzó
	SUCCESS,
	// No había nada que aplicar: la versión local ya estaba al día o el aviso era repetido
	NOOP,
	// Falló: la réplica y su versión quedaron como estaban
	FAILED
}
