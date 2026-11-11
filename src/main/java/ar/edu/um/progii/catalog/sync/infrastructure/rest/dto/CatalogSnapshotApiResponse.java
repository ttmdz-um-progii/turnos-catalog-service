package ar.edu.um.progii.catalog.sync.infrastructure.rest.dto;

import java.util.List;

// Cuerpo de GET /api/synchronization/snapshot (anexo §7). generatedAt no se lee: nadie lo usa
public record CatalogSnapshotApiResponse(long snapshotVersion,
		List<ProfessionalCategoryApiResponse> professionalCategories, List<ProfessionalApiResponse> professionals,
		List<WeeklyScheduleApiResponse> weeklySchedules) {
}
