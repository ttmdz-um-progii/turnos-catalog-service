package ar.edu.um.progii.catalog.sync.domain.model;

public enum SyncAction {
	// La copia local ya está en la versión actual o más adelante
	NOOP,
	// Se aplican los ids que cambiaron entre localVersion + 1 y currentVersion
	INCREMENTAL,
	// La cadena no se puede verificar: se reconstruye todo desde el snapshot
	SNAPSHOT
}
