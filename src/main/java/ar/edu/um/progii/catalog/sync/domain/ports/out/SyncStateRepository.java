package ar.edu.um.progii.catalog.sync.domain.ports.out;

import ar.edu.um.progii.catalog.sync.domain.model.SyncState;

// El estado actual de la copia local. Siempre existe: el servicio arranca con uno vacío
public interface SyncStateRepository {

	SyncState findCurrent();
}
