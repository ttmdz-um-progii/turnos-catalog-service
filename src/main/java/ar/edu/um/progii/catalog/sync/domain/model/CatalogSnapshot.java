package ar.edu.um.progii.catalog.sync.domain.model;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// El catálogo completo de la cátedra en una versión, con las entidades habilitadas y deshabilitadas. Reemplaza entera
// la copia local, y snapshotVersion pasa a ser la versión aplicada
@Getter
public class CatalogSnapshot {

	private final long snapshotVersion;
	private final List<ProfessionalCategory> professionalCategories;
	private final List<Professional> professionals;
	private final List<WeeklySchedule> weeklySchedules;

	@Builder
	public CatalogSnapshot(long snapshotVersion, List<ProfessionalCategory> professionalCategories,
			List<Professional> professionals, List<WeeklySchedule> weeklySchedules) {

		this.snapshotVersion = snapshotVersion;
		this.professionalCategories = validateEntities(professionalCategories, "Professional categories");
		this.professionals = validateEntities(professionals, "Professionals");
		this.weeklySchedules = validateEntities(weeklySchedules, "Weekly schedules");

	}

	// Copia inmutable: quien armó la lista no puede cambiarla después
	private static <T> List<T> validateEntities(List<T> entities, String entitiesName) {

		if (entities == null) {
			throw new IllegalArgumentException(entitiesName + " are mandatory");
		}
		return List.copyOf(entities);

	}
}
