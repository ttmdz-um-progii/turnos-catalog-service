package ar.edu.um.progii.catalog.sync.domain.model;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

// El catálogo completo de la cátedra en una versión, con las entidades habilitadas y deshabilitadas. Reemplaza entera
// la copia local, y snapshotVersion pasa a ser la versión aplicada. Se valida entero antes de tocar la copia: sin ids
// repetidos ni referencias a entidades que no están
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
		validateConsistency(this.professionalCategories, this.professionals, this.weeklySchedules);

	}

	// Cada id aparece una sola vez en su colección, y cada referencia apunta a una entidad del mismo snapshot, aunque
	// esté deshabilitada. Corta en el primer problema: alcanza un motivo para registrar la falla
	private static void validateConsistency(List<ProfessionalCategory> professionalCategories,
			List<Professional> professionals, List<WeeklySchedule> weeklySchedules) {

		Set<Long> professionalCategoryIds = new HashSet<>();
		for (ProfessionalCategory professionalCategory : professionalCategories) {
			if (!professionalCategoryIds.add(professionalCategory.getId())) {
				throw new IllegalArgumentException(
						"Duplicate professional category id " + professionalCategory.getId());
			}
		}

		Set<Long> professionalIds = new HashSet<>();
		for (Professional professional : professionals) {
			if (!professionalIds.add(professional.getId())) {
				throw new IllegalArgumentException("Duplicate professional id " + professional.getId());
			}
			if (!professionalCategoryIds.contains(professional.getCategoryId())) {
				throw new IllegalArgumentException("Professional " + professional.getId()
						+ " references the missing category " + professional.getCategoryId());
			}
		}

		Set<Long> weeklyScheduleIds = new HashSet<>();
		for (WeeklySchedule weeklySchedule : weeklySchedules) {
			if (!weeklyScheduleIds.add(weeklySchedule.getId())) {
				throw new IllegalArgumentException("Duplicate weekly schedule id " + weeklySchedule.getId());
			}
			if (!professionalIds.contains(weeklySchedule.getProfessionalId())) {
				throw new IllegalArgumentException("Weekly schedule " + weeklySchedule.getId()
						+ " references the missing professional " + weeklySchedule.getProfessionalId());
			}
		}

	}

	// Copia inmutable: quien armó la lista no puede cambiarla después
	private static <T> List<T> validateEntities(List<T> entities, String entitiesName) {

		if (entities == null) {
			throw new IllegalArgumentException(entitiesName + " are mandatory");
		}
		return List.copyOf(entities);

	}
}
