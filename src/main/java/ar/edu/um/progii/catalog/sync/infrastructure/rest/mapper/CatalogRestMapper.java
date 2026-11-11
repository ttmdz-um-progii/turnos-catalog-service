package ar.edu.um.progii.catalog.sync.infrastructure.rest.mapper;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.CatalogSnapshotApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.ProfessionalApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.ProfessionalCategoryApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.WeeklyScheduleApiResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// Convierte el snapshot leído de la API de la cátedra en el modelo de dominio. Una colección que falta o una entidad
// que no cumple su modelo lanza IllegalArgumentException, y el adaptador lo trata como snapshot inválido
@Component
public class CatalogRestMapper {

	public CatalogSnapshot toCatalogSnapshot(CatalogSnapshotApiResponse catalogSnapshotResponse) {

		if (catalogSnapshotResponse == null) {
			return null;
		}
		return CatalogSnapshot.builder()
				.snapshotVersion(catalogSnapshotResponse.snapshotVersion())
				.professionalCategories(toProfessionalCategories(catalogSnapshotResponse.professionalCategories()))
				.professionals(toProfessionals(catalogSnapshotResponse.professionals()))
				.weeklySchedules(toWeeklySchedules(catalogSnapshotResponse.weeklySchedules()))
				.build();

	}

	// Una colección que falta queda en null, y CatalogSnapshot la rechaza con su nombre
	private static List<ProfessionalCategory> toProfessionalCategories(
			List<ProfessionalCategoryApiResponse> professionalCategoryResponses) {

		if (professionalCategoryResponses == null) {
			return null;
		}
		List<ProfessionalCategory> professionalCategories = new ArrayList<>();
		for (ProfessionalCategoryApiResponse professionalCategoryResponse : professionalCategoryResponses) {
			professionalCategories.add(ProfessionalCategory.builder()
					.id(professionalCategoryResponse.id())
					.name(professionalCategoryResponse.name())
					.description(professionalCategoryResponse.description())
					.enabled(professionalCategoryResponse.enabled())
					.build());
		}
		return professionalCategories;

	}

	private static List<Professional> toProfessionals(List<ProfessionalApiResponse> professionalResponses) {

		if (professionalResponses == null) {
			return null;
		}
		List<Professional> professionals = new ArrayList<>();
		for (ProfessionalApiResponse professionalResponse : professionalResponses) {
			professionals.add(Professional.builder()
					.id(professionalResponse.id())
					.categoryId(professionalResponse.categoryId())
					.firstName(professionalResponse.firstName())
					.lastName(professionalResponse.lastName())
					.enabled(professionalResponse.enabled())
					.build());
		}
		return professionals;

	}

	private static List<WeeklySchedule> toWeeklySchedules(List<WeeklyScheduleApiResponse> weeklyScheduleResponses) {

		if (weeklyScheduleResponses == null) {
			return null;
		}
		List<WeeklySchedule> weeklySchedules = new ArrayList<>();
		for (WeeklyScheduleApiResponse weeklyScheduleResponse : weeklyScheduleResponses) {
			weeklySchedules.add(WeeklySchedule.builder()
					.id(weeklyScheduleResponse.id())
					.professionalId(weeklyScheduleResponse.professionalId())
					.dayOfWeek(weeklyScheduleResponse.dayOfWeek())
					.startTime(weeklyScheduleResponse.startTime())
					.endTime(weeklyScheduleResponse.endTime())
					.slotDurationMinutes(weeklyScheduleResponse.slotDurationMinutes())
					.enabled(weeklyScheduleResponse.enabled())
					.build());
		}
		return weeklySchedules;

	}
}
