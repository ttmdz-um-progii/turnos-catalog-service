package ar.edu.um.progii.catalog.sync.domain.model;

import lombok.Builder;
import lombok.Getter;

// Las dos versiones que informa la cátedra, leídas juntas para que sean del mismo momento: la última que publicó y
// la más antigua desde la que todavía se puede avanzar de a cambios
@Getter
public class PublishedVersions {

	private final long currentVersion;
	private final long oldestVersion;

	@Builder
	public PublishedVersions(long currentVersion, long oldestVersion) {

		this.currentVersion = currentVersion;
		this.oldestVersion = oldestVersion;

	}
}
