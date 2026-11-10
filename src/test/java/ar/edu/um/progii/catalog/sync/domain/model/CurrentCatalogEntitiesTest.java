package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CurrentCatalogEntitiesTest {

	@Test
	void rejectsNullEntitiesOrMissingIds() {

		assertThatThrownBy(() -> new CurrentCatalogEntities(null, List.of(), List.of(), IdsByEntityType.empty()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CurrentCatalogEntities(List.of(), null, List.of(), IdsByEntityType.empty()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CurrentCatalogEntities(List.of(), List.of(), null, IdsByEntityType.empty()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CurrentCatalogEntities(List.of(), List.of(), List.of(), null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void keepsAnUnmodifiableCopyOfTheEntities() {

		List<ProfessionalCategory> professionalCategories = new ArrayList<>(
				List.of(new ProfessionalCategory(1L, "Clinica medica", null, true)));
		List<Professional> professionals = new ArrayList<>(List.of(new Professional(15L, 1L, "Ana", "Gomez", true)));
		List<WeeklySchedule> weeklySchedules = new ArrayList<>(List.of(new WeeklySchedule(21L, 15L, DayOfWeek.MONDAY,
				LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true)));
		IdsByEntityType idsMissingFromCurrentCatalog = new IdsByEntityType(
				Map.of(CatalogEntityType.PROFESSIONAL, Set.of(22L)));

		CurrentCatalogEntities currentCatalogEntities = new CurrentCatalogEntities(professionalCategories,
				professionals, weeklySchedules, idsMissingFromCurrentCatalog);
		professionalCategories.clear();
		professionals.clear();
		weeklySchedules.clear();

		assertThat(currentCatalogEntities.getProfessionalCategories()).hasSize(1);
		assertThat(currentCatalogEntities.getProfessionals()).hasSize(1);
		assertThat(currentCatalogEntities.getWeeklySchedules()).hasSize(1);
		assertThat(currentCatalogEntities.getIdsMissingFromCurrentCatalog()).isEqualTo(idsMissingFromCurrentCatalog);
		assertThatThrownBy(() -> currentCatalogEntities.getProfessionals().clear())
				.isInstanceOf(UnsupportedOperationException.class);

	}
}
