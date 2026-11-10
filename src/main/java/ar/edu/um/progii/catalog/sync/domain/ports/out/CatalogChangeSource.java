package ar.edu.um.progii.catalog.sync.domain.ports.out;

import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import java.util.Map;

// Lo que la cátedra publicó para avanzar la copia local de a cambios, en el orden en que lo pide SyncDecider.
// Solo lee. Si la cátedra no responde, lanza CatalogSourceUnavailableException
public interface CatalogChangeSource {

	// La última versión publicada y la más antigua desde la que se puede avanzar, leídas juntas
	PublishedVersions readVersions();

	// Los cambios de fromVersion a toVersion, indexados por la versión bajo la que se publicó cada uno. Las versiones
	// sin cambio publicado o con un cambio ilegible no están en el mapa
	Map<Long, VersionChanges> readChanges(long fromVersion, long toVersion);

	CurrentCatalogEntities readCurrentCatalog(IdsByEntityType idsToRead);
}
