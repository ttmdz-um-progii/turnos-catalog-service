package ar.edu.um.progii.catalog.sync.domain.ports.out;

import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncState;

// Único escritor de la copia local del catálogo y de su estado. Cada método guarda todo junto o nada: la copia nunca
// queda con datos de una versión y el estado de otra
public interface CatalogReplicaWriter {

	// Reemplaza la copia entera por el snapshot y guarda el estado nuevo y la ejecución exitosa
	void replaceAll(CatalogSnapshot catalogSnapshot, SyncState stateAfterSnapshot, SyncRun successfulRun);

	// Guarda el estado tras una falla y la ejecución fallida, sin tocar la copia
	void recordFailure(SyncState stateAfterFailure, SyncRun failedRun);
}
