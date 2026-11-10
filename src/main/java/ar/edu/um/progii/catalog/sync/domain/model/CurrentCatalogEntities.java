package ar.edu.um.progii.catalog.sync.domain.model;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// El estado actual en la cátedra de los ids pendientes: las entidades que se pudieron leer y los ids que no
@Getter
public class CurrentCatalogEntities {

	private final List<ProfessionalCategory> professionalCategories;
	private final List<Professional> professionals;
	private final List<WeeklySchedule> weeklySchedules;
	// Los ids que la cátedra no tiene o que publicó ilegibles: sin sus datos no se pueden aplicar
	private final IdsByEntityType idsMissingFromCurrentCatalog;

	@Builder
	public CurrentCatalogEntities(List<ProfessionalCategory> professionalCategories, List<Professional> professionals,
			List<WeeklySchedule> weeklySchedules, IdsByEntityType idsMissingFromCurrentCatalog) {

		this.professionalCategories = validateEntities(professionalCategories, "Professional categories");
		this.professionals = validateEntities(professionals, "Professionals");
		this.weeklySchedules = validateEntities(weeklySchedules, "Weekly schedules");
		this.idsMissingFromCurrentCatalog = validateIdsMissingFromCurrentCatalog(idsMissingFromCurrentCatalog);

	}

	// Copia inmutable: quien armó la lista no puede cambiarla después
	private static <T> List<T> validateEntities(List<T> entities, String entitiesName) {

		if (entities == null) {
			throw new IllegalArgumentException(entitiesName + " are mandatory");
		}
		return List.copyOf(entities);

	}

	private static IdsByEntityType validateIdsMissingFromCurrentCatalog(IdsByEntityType idsMissingFromCurrentCatalog) {

		if (idsMissingFromCurrentCatalog == null) {
			throw new IllegalArgumentException("Ids missing from the current catalog are mandatory");
		}
		return idsMissingFromCurrentCatalog;

	}
}
