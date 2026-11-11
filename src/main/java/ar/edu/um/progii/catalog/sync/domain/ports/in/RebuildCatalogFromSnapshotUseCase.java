package ar.edu.um.progii.catalog.sync.domain.ports.in;

import ar.edu.um.progii.catalog.sync.domain.model.SyncRun;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;

// Reemplaza la copia local entera por el snapshot de la cátedra (CU-2). Devuelve la ejecución registrada, que dice
// hasta qué versión llegó. Si falla, registra la falla, deja la copia como estaba y lanza la excepción del motivo
public interface RebuildCatalogFromSnapshotUseCase {

	SyncRun rebuildCatalogFromSnapshot(SyncTrigger trigger);
}
