package ar.edu.um.progii.catalog.sync.domain.ports.in;

import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;

// Lleva la copia local hasta la última versión publicada por la cátedra (CU-1): de a cambios cuando se puede verificar
// la cadena, con el snapshot cuando no. Si otra sincronización está corriendo lanza SyncAlreadyRunningException; si
// falla, registra la falla, deja la copia como estaba y lanza la excepción del motivo
public interface SyncCatalogUseCase {

	void syncCatalog(SyncTrigger trigger);
}
