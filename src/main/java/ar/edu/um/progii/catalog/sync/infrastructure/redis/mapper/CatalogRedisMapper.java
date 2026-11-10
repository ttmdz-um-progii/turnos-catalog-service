package ar.edu.um.progii.catalog.sync.infrastructure.redis.mapper;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.ChangedIdsRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.ProfessionalCategoryRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.ProfessionalRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.VersionChangesRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.WeeklyScheduleRedisDto;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

// Convierte lo leído de Redis en modelos de dominio. Un dato que falta o no cumple el modelo lanza
// IllegalArgumentException, y el adaptador lo trata como ilegible
@Component
public class CatalogRedisMapper {

	// metadataFields son los campos del Hash catedra:sync:metadata (anexo §14.3)
	public PublishedVersions toPublishedVersions(Map<String, String> metadataFields) {

		if (metadataFields == null) {
			return null;
		}
		// Long.parseLong lanza NumberFormatException, que es una IllegalArgumentException, si falta el campo
		return PublishedVersions.builder()
				.currentVersion(Long.parseLong(metadataFields.get("currentVersion")))
				.oldestVersion(Long.parseLong(metadataFields.get("oldestAvailableVersion")))
				.build();

	}

	public VersionChanges toVersionChanges(VersionChangesRedisDto versionChangesDto) {

		if (versionChangesDto == null) {
			return null;
		}
		return VersionChanges.builder()
				.version(versionChangesDto.version())
				.changedIds(toChangedIds(versionChangesDto.changes()))
				.build();

	}

	public ProfessionalCategory toProfessionalCategory(ProfessionalCategoryRedisDto professionalCategoryDto) {

		if (professionalCategoryDto == null) {
			return null;
		}
		return ProfessionalCategory.builder()
				.id(professionalCategoryDto.id())
				.name(professionalCategoryDto.name())
				.description(professionalCategoryDto.description())
				.enabled(professionalCategoryDto.enabled())
				.build();

	}

	public Professional toProfessional(ProfessionalRedisDto professionalDto) {

		if (professionalDto == null) {
			return null;
		}
		return Professional.builder()
				.id(professionalDto.id())
				.categoryId(professionalDto.categoryId())
				.firstName(professionalDto.firstName())
				.lastName(professionalDto.lastName())
				.enabled(professionalDto.enabled())
				.build();

	}

	public WeeklySchedule toWeeklySchedule(WeeklyScheduleRedisDto weeklyScheduleDto) {

		if (weeklyScheduleDto == null) {
			return null;
		}
		return WeeklySchedule.builder()
				.id(weeklyScheduleDto.id())
				.professionalId(weeklyScheduleDto.professionalId())
				.dayOfWeek(weeklyScheduleDto.dayOfWeek())
				.startTime(weeklyScheduleDto.startTime())
				.endTime(weeklyScheduleDto.endTime())
				.slotDurationMinutes(weeklyScheduleDto.slotDurationMinutes())
				.enabled(weeklyScheduleDto.enabled())
				.build();

	}

	private static IdsByEntityType toChangedIds(ChangedIdsRedisDto changedIdsDto) {

		if (changedIdsDto == null) {
			throw new IllegalArgumentException("Changed ids are mandatory");
		}
		Map<CatalogEntityType, Set<Long>> changedIdsByType = new EnumMap<>(CatalogEntityType.class);
		changedIdsByType.put(CatalogEntityType.PROFESSIONAL_CATEGORY,
				toIdSet(changedIdsDto.professionalCategories(), "professionalCategories"));
		changedIdsByType.put(CatalogEntityType.PROFESSIONAL, toIdSet(changedIdsDto.professionals(), "professionals"));
		changedIdsByType.put(CatalogEntityType.WEEKLY_SCHEDULE,
				toIdSet(changedIdsDto.weeklySchedules(), "weeklySchedules"));
		return new IdsByEntityType(changedIdsByType);

	}

	private static Set<Long> toIdSet(List<Long> changedIds, String fieldName) {

		if (changedIds == null) {
			throw new IllegalArgumentException("The list " + fieldName + " is mandatory");
		}
		return new HashSet<>(changedIds);

	}
}
