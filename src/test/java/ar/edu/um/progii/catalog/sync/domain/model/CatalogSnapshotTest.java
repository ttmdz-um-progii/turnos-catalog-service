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

	private static final ProfessionalCategory CLINICAL_CATEGORY = new ProfessionalCategory(1L, "Clinica medica", null,
			true);
	private static final Professional ANA_GOMEZ = new Professional(15L, 1L, "Ana", "Gomez", true);
	private static final WeeklySchedule ANA_MONDAY_SCHEDULE = new WeeklySchedule(21L, 15L, DayOfWeek.MONDAY,
			LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true);

	@Test
	void rejectsDuplicateIds() {

		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(CLINICAL_CATEGORY,
				new ProfessionalCategory(1L, "Pediatria", null, true)), List.of(), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Duplicate professional category id 1");
		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(CLINICAL_CATEGORY),
				List.of(ANA_GOMEZ, new Professional(15L, 1L, "Luis", "Perez", true)), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Duplicate professional id 15");
		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(CLINICAL_CATEGORY), List.of(ANA_GOMEZ),
				List.of(ANA_MONDAY_SCHEDULE, new WeeklySchedule(21L, 15L, DayOfWeek.TUESDAY, LocalTime.of(14, 0),
						LocalTime.of(18, 0), 20, true))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Duplicate weekly schedule id 21");

	}

	@Test
	void rejectsAProfessionalWithAMissingCategory() {

		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(CLINICAL_CATEGORY),
				List.of(new Professional(22L, 3L, "Luis", "Perez", true)), List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Professional 22 references the missing category 3");

	}

	@Test
	void rejectsAScheduleWithAMissingProfessional() {

		assertThatThrownBy(() -> new CatalogSnapshot(7L, List.of(CLINICAL_CATEGORY), List.of(ANA_GOMEZ),
				List.of(new WeeklySchedule(30L, 22L, DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), 15,
						true))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Weekly schedule 30 references the missing professional 22");

	}

	@Test
	void acceptsReferencesToDisabledEntities() {

		ProfessionalCategory disabledCategory = new ProfessionalCategory(1L, "Clinica medica", null, false);
		Professional disabledProfessional = new Professional(15L, 1L, "Ana", "Gomez", false);

		CatalogSnapshot catalogSnapshot = new CatalogSnapshot(7L, List.of(disabledCategory),
				List.of(disabledProfessional), List.of(ANA_MONDAY_SCHEDULE));

		assertThat(catalogSnapshot.getWeeklySchedules()).containsExactly(ANA_MONDAY_SCHEDULE);

	}

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
