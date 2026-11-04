package ar.edu.um.progii.catalog.sync.domain.model;

import lombok.Builder;
import lombok.Getter;

// Un cambio publicado por la cátedra: los ids que cambiaron en una versión, por tipo de entidad
@Getter
public class VersionChanges {

	// La versión que declara el propio cambio. Si la cadena está rota, puede no coincidir con la versión bajo la
	// que se publicó
	private final long version;
	private final IdsByEntityType changedIds;

	@Builder
	public VersionChanges(long version, IdsByEntityType changedIds) {

		this.version = version;
		this.changedIds = validateChangedIds(changedIds);

	}

	private static IdsByEntityType validateChangedIds(IdsByEntityType changedIds) {

		if (changedIds == null) {
			throw new IllegalArgumentException("Changed ids are mandatory");
		}
		return changedIds;

	}
}
