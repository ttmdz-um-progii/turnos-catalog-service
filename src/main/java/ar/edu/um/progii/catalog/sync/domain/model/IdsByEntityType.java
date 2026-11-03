package ar.edu.um.progii.catalog.sync.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

// Ids de entidades del catálogo agrupados por tipo: los que cambiaron en una versión, los pendientes de aplicar o
// los que faltan en el catálogo actual de la cátedra
@Getter
@EqualsAndHashCode
@ToString
public class IdsByEntityType {

	private final Map<CatalogEntityType, Set<Long>> idsByType;

	public IdsByEntityType(Map<CatalogEntityType, Set<Long>> idsByType) {

		this.idsByType = validateIdsByType(idsByType);

	}

	public static IdsByEntityType empty() {

		return new IdsByEntityType(Map.of());

	}

	public boolean isEmpty() {

		return idsByType.isEmpty();

	}

	// Un id que está en los dos grupos queda una sola vez
	public IdsByEntityType union(IdsByEntityType otherIds) {

		Map<CatalogEntityType, Set<Long>> mergedIdsByType = new EnumMap<>(CatalogEntityType.class);
		addIdsByType(mergedIdsByType, idsByType);
		addIdsByType(mergedIdsByType, otherIds.getIdsByType());
		return new IdsByEntityType(mergedIdsByType);

	}

	// Si algún id de este grupo también está en otherIds con el mismo tipo
	public boolean containsAnyOf(IdsByEntityType otherIds) {

		for (CatalogEntityType entityType : idsByType.keySet()) {
			Set<Long> otherIdsOfType = otherIds.getIdsByType().getOrDefault(entityType, Set.of());
			for (Long entityId : idsByType.get(entityType)) {
				if (otherIdsOfType.contains(entityId)) {
					return true;
				}
			}
		}
		return false;

	}

	private static void addIdsByType(Map<CatalogEntityType, Set<Long>> mergedIdsByType,
			Map<CatalogEntityType, Set<Long>> idsByTypeToAdd) {

		for (CatalogEntityType entityType : idsByTypeToAdd.keySet()) {
			if (!mergedIdsByType.containsKey(entityType)) {
				mergedIdsByType.put(entityType, new HashSet<>());
			}
			// Un Set no admite repetidos
			mergedIdsByType.get(entityType).addAll(idsByTypeToAdd.get(entityType));
		}

	}

	private static Map<CatalogEntityType, Set<Long>> validateIdsByType(Map<CatalogEntityType, Set<Long>> idsByType) {

		if (idsByType == null) {
			throw new IllegalArgumentException("Ids by type are mandatory");
		}
		// Copia inmutable: quien armó el mapa no puede cambiarlo después. Los tipos sin ids no se guardan, así dos
		// grupos con los mismos ids son iguales aunque uno traiga un tipo vacío
		Map<CatalogEntityType, Set<Long>> copiedIdsByType = new EnumMap<>(CatalogEntityType.class);
		for (CatalogEntityType entityType : idsByType.keySet()) {
			if (entityType == null) {
				throw new IllegalArgumentException("Entity type is mandatory");
			}
			Set<Long> idsOfType = idsByType.get(entityType);
			if (idsOfType == null) {
				throw new IllegalArgumentException("Ids of " + entityType + " are mandatory");
			}
			if (!idsOfType.isEmpty()) {
				copiedIdsByType.put(entityType, Set.copyOf(idsOfType));
			}
		}
		return Collections.unmodifiableMap(copiedIdsByType);

	}
}
