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

import org.junit.jupiter.api.Test;

class CatalogSnapshotTest {

	@Test
	void rejectsAMissingCollection() {

		assertThatThrownBy(() -> new CatalogSnapshot(7L, null, List.of(), List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(), null, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(), List.of(), null))
				.isInstanceOf(IllegalArgumentException.class);

	}

	@Test
	void keepsAnUnmodifiableCopyOfTheEntities() {

		List<ProfessionalCategory> professionalCategories = new ArrayList<>(
				List.of(new ProfessionalCategory(1L, "Clinica medica", null, true)));
		List<Professional> professionals = new ArrayList<>(List.of(new Professional(15L, 1L, "Ana", "Gomez", true)));
		List<WeeklySchedule> weeklySchedules = new ArrayList<>(List.of(new WeeklySchedule(21L, 15L, DayOfWeek.MONDAY,
				LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true)));

		CatalogSnapshot catalogSnapshot = new CatalogSnapshot(7L, professionalCategories, professionals,
				weeklySchedules);
		professionalCategories.clear();
		professionals.clear();
		weeklySchedules.clear();

		assertThat(catalogSnapshot.getSnapshotVersion()).isEqualTo(7L);
		assertThat(catalogSnapshot.getProfessionalCategories()).hasSize(1);
		assertThat(catalogSnapshot.getProfessionals()).hasSize(1);
		assertThat(catalogSnapshot.getWeeklySchedules()).hasSize(1);
		assertThatThrownBy(() -> catalogSnapshot.getProfessionals().clear())
				.isInstanceOf(UnsupportedOperationException.class);

	}
}
