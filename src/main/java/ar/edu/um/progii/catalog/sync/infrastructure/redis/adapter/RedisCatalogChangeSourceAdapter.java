package ar.edu.um.progii.catalog.sync.infrastructure.redis.adapter;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogChangeSource;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.ProfessionalCategoryRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.ProfessionalRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.VersionChangesRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.dto.WeeklyScheduleRedisDto;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.mapper.CatalogRedisMapper;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Lee del Redis de la cátedra (anexo §14) con comandos de solo lectura: HGETALL de catedra:sync:metadata, MGET de
// catedra:sync:changes:{version} y HMGET de los Hashes de entidades, donde el campo es el id y el valor un JSON.
// Un JSON que no se puede leer cuenta como ausente: SyncDecider lo trata como cadena rota o id faltante
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCatalogChangeSourceAdapter implements CatalogChangeSource {

	private static final String METADATA_KEY = "catedra:sync:metadata";
	private static final String CHANGES_KEY_PREFIX = "catedra:sync:changes:";

	private final StringRedisTemplate redisTemplate;
	private final JsonMapper jsonMapper;
	private final CatalogRedisMapper catalogRedisMapper;

	@Override
	public PublishedVersions readVersions() {

		Map<String, String> metadataFields;
		try {
			metadataFields = redisTemplate.<String, String>opsForHash().entries(METADATA_KEY);
		} catch (DataAccessException redisException) {
			throw new CatalogSourceUnavailableException("Could not read " + METADATA_KEY, redisException);
		}

		if (metadataFields.isEmpty()) {
			throw new CatalogSourceUnavailableException(METADATA_KEY + " does not exist");
		}
		try {
			return catalogRedisMapper.toPublishedVersions(metadataFields);
		} catch (IllegalArgumentException unreadableMetadataException) {
			throw new CatalogSourceUnavailableException(METADATA_KEY + " is unreadable: " + metadataFields,
					unreadableMetadataException);
		}

	}

	@Override
	public Map<Long, VersionChanges> readChanges(long fromVersion, long toVersion) {

		List<String> changesKeys = new ArrayList<>();
		for (long version = fromVersion; version <= toVersion; version++) {
			changesKeys.add(CHANGES_KEY_PREFIX + version);
		}

		List<String> changesJsons;
		try {
			changesJsons = redisTemplate.opsForValue().multiGet(changesKeys);
		} catch (DataAccessException redisException) {
			throw new CatalogSourceUnavailableException("Could not read " + CHANGES_KEY_PREFIX + fromVersion + " to "
					+ toVersion, redisException);
		}

		// MGET devuelve un valor por clave, en el mismo orden, y null donde la clave no existe
		Map<Long, VersionChanges> changesByVersion = new HashMap<>();
		for (int keyIndex = 0; keyIndex < changesKeys.size(); keyIndex++) {
			long publishedVersion = fromVersion + keyIndex;
			String changesJson = changesJsons.get(keyIndex);
			if (changesJson == null) {
				continue;
			}
			try {
				VersionChangesRedisDto versionChangesDto = jsonMapper.readValue(changesJson,
						VersionChangesRedisDto.class);
				changesByVersion.put(publishedVersion, catalogRedisMapper.toVersionChanges(versionChangesDto));
			} catch (JacksonException | IllegalArgumentException unreadableChangesException) {
				log.warn("Unreadable {}{}: {}", CHANGES_KEY_PREFIX, publishedVersion,
						unreadableChangesException.getMessage());
			}
		}
		return changesByVersion;

	}

	@Override
	public CurrentCatalogEntities readCurrentCatalog(IdsByEntityType idsToRead) {

		List<ProfessionalCategory> professionalCategories = new ArrayList<>();
		List<Professional> professionals = new ArrayList<>();
		List<WeeklySchedule> weeklySchedules = new ArrayList<>();
		Map<CatalogEntityType, Set<Long>> missingIdsByType = new EnumMap<>(CatalogEntityType.class);

		for (CatalogEntityType entityType : idsToRead.getIdsByType().keySet()) {
			String entitiesKey = entitiesKey(entityType);
			List<Long> entityIds = new ArrayList<>(idsToRead.getIdsByType().get(entityType));
			List<String> entityFields = new ArrayList<>();
			for (Long entityId : entityIds) {
				entityFields.add(entityId.toString());
			}

			List<String> entityJsons;
			try {
				entityJsons = redisTemplate.<String, String>opsForHash().multiGet(entitiesKey, entityFields);
			} catch (DataAccessException redisException) {
				throw new CatalogSourceUnavailableException("Could not read " + entitiesKey, redisException);
			}

			// HMGET devuelve un valor por campo, en el mismo orden, y null donde el campo no existe
			Set<Long> missingIdsOfType = new HashSet<>();
			for (int fieldIndex = 0; fieldIndex < entityIds.size(); fieldIndex++) {
				Long entityId = entityIds.get(fieldIndex);
				String entityJson = entityJsons.get(fieldIndex);
				if (entityJson == null) {
					missingIdsOfType.add(entityId);
					continue;
				}
				try {
					switch (entityType) {
						case PROFESSIONAL_CATEGORY -> professionalCategories.add(catalogRedisMapper
								.toProfessionalCategory(
										jsonMapper.readValue(entityJson, ProfessionalCategoryRedisDto.class)));
						case PROFESSIONAL -> professionals.add(catalogRedisMapper
								.toProfessional(jsonMapper.readValue(entityJson, ProfessionalRedisDto.class)));
						case WEEKLY_SCHEDULE -> weeklySchedules.add(catalogRedisMapper
								.toWeeklySchedule(jsonMapper.readValue(entityJson, WeeklyScheduleRedisDto.class)));
					}
				} catch (JacksonException | IllegalArgumentException unreadableEntityException) {
					log.warn("Unreadable field {} of {}: {}", entityId, entitiesKey,
							unreadableEntityException.getMessage());
					missingIdsOfType.add(entityId);
				}
			}
			missingIdsByType.put(entityType, missingIdsOfType);
		}

		return CurrentCatalogEntities.builder()
				.professionalCategories(professionalCategories)
				.professionals(professionals)
				.weeklySchedules(weeklySchedules)
				.idsMissingFromCurrentCatalog(new IdsByEntityType(missingIdsByType))
				.build();

	}

	private static String entitiesKey(CatalogEntityType entityType) {

		return switch (entityType) {
			case PROFESSIONAL_CATEGORY -> "catedra:sync:professional-categories";
			case PROFESSIONAL -> "catedra:sync:professionals";
			case WEEKLY_SCHEDULE -> "catedra:sync:weekly-schedules";
		};

	}
}
