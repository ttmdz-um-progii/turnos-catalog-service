package ar.edu.um.progii.catalog.sync.domain.ports.out;

import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;

// El catálogo completo que publica la cátedra, para reconstruir la copia local desde cero. Solo lee.
// Si la cátedra no responde, lanza CatalogSourceUnavailableException; si rechaza las credenciales del servicio,
// CatalogCredentialsRejectedException; y si lo que devuelve no se puede leer, le falta una colección o tiene datos
// inconsistentes, InvalidCatalogSnapshotException
public interface CatalogSnapshotSource {

	CatalogSnapshot readSnapshot();
}
