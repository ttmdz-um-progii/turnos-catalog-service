package ar.edu.um.progii.catalog.sync.domain.ports.in;

import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;

// Reemplaza la copia local entera por el snapshot de la cátedra (CU-2) y registra la ejecución. Si falla, registra la
// falla, deja la copia como estaba y lanza la excepción del motivo
public interface RebuildCatalogFromSnapshotUseCase {

	void rebuildCatalogFromSnapshot(SyncTrigger trigger);
}
