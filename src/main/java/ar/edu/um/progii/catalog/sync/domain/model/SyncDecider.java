package ar.edu.um.progii.catalog.sync.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Regla central de la sincronización: decide NOOP, INCREMENTAL o SNAPSHOT.
// Va en tres etapas, una por cada dato y en el orden en que el caso de uso los obtiene:
// 1. las versiones: localVersion (la de la copia local), currentVersion (la última publicada por la cátedra) y
// oldestVersion (la más antigua desde la que se puede avanzar de a cambios);
// 2. la cadena de cambios de localVersion + 1 a currentVersion;
// 3. los ids pendientes que el catálogo actual de la cátedra no devolvió.
// Cada etapa que decide corta el proceso: el caso de uso no obtiene datos que ya no hacen falta.
public final class SyncDecider {

	private SyncDecider() {

	}

	// Etapa 1. Vacío significa que hace falta leer los cambios de localVersion + 1 a currentVersion
	public static Optional<SyncDecision> decideByVersions(Long localVersion, long currentVersion, long oldestVersion) {

		if (localVersion == null) {
			return Optional.of(SyncDecision.snapshot());
		}
		// Va antes que localVersion < oldestVersion: un aviso duplicado o viejo nunca dispara trabajo ni retrocede
		if (currentVersion <= localVersion) {
			return Optional.of(SyncDecision.noop());
		}
		// Los cambios desde localVersion + 1 ya no están disponibles (anexo §18.2)
		if (localVersion < oldestVersion) {
			return Optional.of(SyncDecision.snapshot());
		}
		return Optional.empty();

	}

	// Etapa 2. Supone que la etapa 1 devolvió vacío. changesByVersion trae cada cambio indexado por la versión bajo
	// la que se publicó. Si la cadena está completa, devuelve un INCREMENTAL con los ids pendientes: son los que el
	// caso de uso tiene que pedirle al catálogo actual de la cátedra antes de la etapa 3
	public static SyncDecision decideByChain(long localVersion, long currentVersion,
			Map<Long, VersionChanges> changesByVersion) {

		Objects.requireNonNull(changesByVersion, "changesByVersion");
		// Si la etapa 1 no devolvió vacío, seguir podría dar un INCREMENTAL que hace retroceder la copia local
		if (currentVersion <= localVersion) {
			throw new IllegalArgumentException("The chain is only read when currentVersion is ahead of localVersion");
		}

		IdsByEntityType pendingIdsByType = IdsByEntityType.empty();
		for (long expectedVersion = localVersion + 1; expectedVersion <= currentVersion; expectedVersion++) {
			VersionChanges versionChanges = changesByVersion.get(expectedVersion);
			if (versionChanges == null || versionChanges.getVersion() != expectedVersion) {
				return SyncDecision.snapshot();
			}
			pendingIdsByType = pendingIdsByType.union(versionChanges.getChangedIds());
		}
		return SyncDecision.incremental(pendingIdsByType);

	}

	// Etapa 3. Recibe el INCREMENTAL de la etapa 2 e idsMissingFromCurrentCatalog: los ids pendientes que el
	// catálogo actual de la cátedra no devolvió. Si falta alguno, no hay datos para aplicarlo
	public static SyncDecision decideByMissingIds(SyncDecision incrementalDecision,
			IdsByEntityType idsMissingFromCurrentCatalog) {

		Objects.requireNonNull(incrementalDecision, "incrementalDecision");
		Objects.requireNonNull(idsMissingFromCurrentCatalog, "idsMissingFromCurrentCatalog");
		if (incrementalDecision.getAction() != SyncAction.INCREMENTAL) {
			throw new IllegalArgumentException("Only an incremental decision can be checked for missing ids");
		}

		if (incrementalDecision.getIdsToApply().containsAnyOf(idsMissingFromCurrentCatalog)) {
			return SyncDecision.snapshot();
		}
		return incrementalDecision;

	}
}
