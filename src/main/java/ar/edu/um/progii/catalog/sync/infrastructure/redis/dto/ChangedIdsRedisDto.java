package ar.edu.um.progii.catalog.sync.infrastructure.redis.dto;

import java.util.List;

// Campo changes de VersionChangesRedisDto: los ids que cambiaron, uno por tipo de entidad
public record ChangedIdsRedisDto(List<Long> professionalCategories, List<Long> professionals,
		List<Long> weeklySchedules) {
}
