package ar.edu.um.progii.catalog.sync.infrastructure.redis.adapter;

import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.PROFESSIONAL_CATEGORY;
import static ar.edu.um.progii.catalog.sync.domain.model.CatalogEntityType.WEEKLY_SCHEDULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.domain.model.CurrentCatalogEntities;
import ar.edu.um.progii.catalog.sync.domain.model.IdsByEntityType;
import ar.edu.um.progii.catalog.sync.domain.model.PublishedVersions;
import ar.edu.um.progii.catalog.sync.domain.model.VersionChanges;
import ar.edu.um.progii.catalog.sync.infrastructure.redis.mapper.CatalogRedisMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

// Corre contra el Redis de TestcontainersConfiguration, cargado con los datos de ejemplo del anexo (§14)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisCatalogChangeSourceAdapterTest {

	@Autowired
	private RedisCatalogChangeSourceAdapter redisCatalogChangeSourceAdapter;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private CatalogRedisMapper catalogRedisMapper;

	@BeforeEach
	void loadTheAnnexExample() {

		redisTemplate.opsForHash()
				.putAll("catedra:sync:metadata", Map.of("currentVersion", "7", "oldestAvailableVersion", "4",
						"publishedAt", "2026-09-10T13:44:59Z"));
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:6",
						"{\"version\":6,\"changes\":{\"professionalCategories\":[3],\"professionals\":[22],\"weeklySchedules\":[]}}");
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:7",
						"{\"version\":7,\"changes\":{\"professionalCategories\":[],\"professionals\":[15],\"weeklySchedules\":[]}}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professional-categories", "1",
						"{\"id\":1,\"name\":\"Clinica medica\",\"description\":\"Atencion clinica general\",\"enabled\":true,"
								+ "\"createdAt\":\"2026-07-01T12:00:00Z\",\"updatedAt\":\"2026-09-10T13:40:00Z\"}");
		redisTemplate.opsForHash()
				.put("catedra:sync:professionals", "15",
						"{\"id\":15,\"categoryId\":1,\"firstName\":\"Ana\",\"lastName\":\"Gómez\",\"enabled\":true,"
								+ "\"createdAt\":\"2026-07-01T12:00:00Z\",\"updatedAt\":\"2026-09-10T13:40:00Z\"}");
		redisTemplate.opsForHash()
				.put("catedra:sync:weekly-schedules", "21",
						"{\"id\":21,\"professionalId\":15,\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00:00\","
								+ "\"endTime\":\"12:00\",\"slotDurationMinutes\":30,\"enabled\":true,"
								+ "\"createdAt\":\"2026-07-01T12:00:00Z\",\"updatedAt\":\"2026-09-10T13:40:00Z\"}");

	}

	@AfterEach
	void deleteTheCatalogKeys() {

		redisTemplate.delete(redisTemplate.keys("catedra:sync:*"));

	}

	@Test
	void readsTheVersionPair() {

		PublishedVersions publishedVersions = redisCatalogChangeSourceAdapter.readVersions();

		assertThat(publishedVersions.getCurrentVersion()).isEqualTo(7L);
		assertThat(publishedVersions.getOldestVersion()).isEqualTo(4L);

	}

	@Test
	void failsWithoutMetadata() {

		redisTemplate.delete("catedra:sync:metadata");

		assertThatThrownBy(() -> redisCatalogChangeSourceAdapter.readVersions())
				.isInstanceOf(CatalogSourceUnavailableException.class);

	}

	@Test
	void failsWithUnreadableMetadata() {

		redisTemplate.opsForHash().put("catedra:sync:metadata", "currentVersion", "seven");

		assertThatThrownBy(() -> redisCatalogChangeSourceAdapter.readVersions())
				.isInstanceOf(CatalogSourceUnavailableException.class);

	}

	@Test
	void readsTheChangesOfARange() {

		Map<Long, VersionChanges> changesByVersion = redisCatalogChangeSourceAdapter.readChanges(6, 7);

		assertThat(changesByVersion).containsOnlyKeys(6L, 7L);
		assertThat(changesByVersion.get(6L).getVersion()).isEqualTo(6L);
		assertThat(changesByVersion.get(6L).getChangedIds())
				.isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL_CATEGORY, Set.of(3L), PROFESSIONAL, Set.of(22L))));
		assertThat(changesByVersion.get(7L).getChangedIds())
				.isEqualTo(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(15L))));

	}

	@Test
	void leavesOutMissingAndUnreadableVersions() {

		redisTemplate.opsForValue().set("catedra:sync:changes:8", "{not json");
		// Le falta la lista de horarios
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:9",
						"{\"version\":9,\"changes\":{\"professionalCategories\":[],\"professionals\":[15]}}");

		// La 5 no existe
		Map<Long, VersionChanges> changesByVersion = redisCatalogChangeSourceAdapter.readChanges(5, 9);

		assertThat(changesByVersion).containsOnlyKeys(6L, 7L);

	}

	@Test
	void keepsTheVersionDeclaredByTheChange() {

		// Publicado bajo la 8, pero declara la 7: la cadena está rota y lo detecta SyncDecider
		redisTemplate.opsForValue()
				.set("catedra:sync:changes:8",
						"{\"version\":7,\"changes\":{\"professionalCategories\":[],\"professionals\":[],\"weeklySchedules\":[]}}");

		Map<Long, VersionChanges> changesByVersion = redisCatalogChangeSourceAdapter.readChanges(8, 8);

		assertThat(changesByVersion.get(8L).getVersion()).isEqualTo(7L);

	}

	@Test
	void readsTheRequestedEntities() {

		IdsByEntityType idsToRead = new IdsByEntityType(
				Map.of(PROFESSIONAL_CATEGORY, Set.of(1L), PROFESSIONAL, Set.of(15L), WEEKLY_SCHEDULE, Set.of(21L)));

		CurrentCatalogEntities currentCatalogEntities = redisCatalogChangeSourceAdapter.readCurrentCatalog(idsToRead);

		ProfessionalCategory professionalCategory = currentCatalogEntities.getProfessionalCategories().getFirst();
		assertThat(professionalCategory.getId()).isEqualTo(1L);
		assertThat(professionalCategory.getName()).isEqualTo("Clinica medica");
		assertThat(professionalCategory.getDescription()).isEqualTo("Atencion clinica general");
		assertThat(professionalCategory.isEnabled()).isTrue();

		Professional professional = currentCatalogEntities.getProfessionals().getFirst();
		assertThat(professional.getId()).isEqualTo(15L);
		assertThat(professional.getCategoryId()).isEqualTo(1L);
		assertThat(professional.getFirstName()).isEqualTo("Ana");
		assertThat(professional.getLastName()).isEqualTo("Gómez");
		assertThat(professional.getSearchName()).isEqualTo("ana gomez");
		assertThat(professional.isEnabled()).isTrue();

		WeeklySchedule weeklySchedule = currentCatalogEntities.getWeeklySchedules().getFirst();
		assertThat(weeklySchedule.getId()).isEqualTo(21L);
		assertThat(weeklySchedule.getProfessionalId()).isEqualTo(15L);
		assertThat(weeklySchedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		// La cátedra puede mandar HH:mm:ss o HH:mm
		assertThat(weeklySchedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(weeklySchedule.getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(weeklySchedule.getSlotDurationMinutes()).isEqualTo(30);
		assertThat(weeklySchedule.isEnabled()).isTrue();

		assertThat(currentCatalogEntities.getIdsMissingFromCurrentCatalog().isEmpty()).isTrue();

	}

	@Test
	void reportsMissingAndUnreadableEntitiesAsMissing() {

		redisTemplate.opsForHash().put("catedra:sync:professionals", "16", "{not json");
		// Le falta el nombre, que el modelo exige
		redisTemplate.opsForHash()
				.put("catedra:sync:professionals", "17",
						"{\"id\":17,\"categoryId\":1,\"lastName\":\"Perez\",\"enabled\":true}");
		IdsByEntityType idsToRead = new IdsByEntityType(
				Map.of(PROFESSIONAL_CATEGORY, Set.of(1L, 3L), PROFESSIONAL, Set.of(15L, 16L, 17L, 99L)));

		CurrentCatalogEntities currentCatalogEntities = redisCatalogChangeSourceAdapter.readCurrentCatalog(idsToRead);

		assertThat(currentCatalogEntities.getProfessionalCategories()).hasSize(1);
		assertThat(currentCatalogEntities.getProfessionals()).hasSize(1);
		assertThat(currentCatalogEntities.getIdsMissingFromCurrentCatalog()).isEqualTo(
				new IdsByEntityType(Map.of(PROFESSIONAL_CATEGORY, Set.of(3L), PROFESSIONAL, Set.of(16L, 17L, 99L))));

	}

	@Test
	void failsWhenTheSourceDoesNotAnswer() throws IOException {

		// Un puerto que se abre y se cierra enseguida: ahí no escucha nadie
		int closedPort;
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			closedPort = serverSocket.getLocalPort();
		}
		LettuceConnectionFactory unreachableConnectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration("localhost", closedPort));
		unreachableConnectionFactory.afterPropertiesSet();
		unreachableConnectionFactory.start();
		RedisCatalogChangeSourceAdapter unreachableAdapter = new RedisCatalogChangeSourceAdapter(
				new StringRedisTemplate(unreachableConnectionFactory), jsonMapper, catalogRedisMapper);

		try {
			assertThatThrownBy(() -> unreachableAdapter.readVersions())
					.isInstanceOf(CatalogSourceUnavailableException.class);
			assertThatThrownBy(() -> unreachableAdapter.readChanges(6, 7))
					.isInstanceOf(CatalogSourceUnavailableException.class);
			assertThatThrownBy(() -> unreachableAdapter
					.readCurrentCatalog(new IdsByEntityType(Map.of(PROFESSIONAL, Set.of(15L)))))
					.isInstanceOf(CatalogSourceUnavailableException.class);
		} finally {
			unreachableConnectionFactory.destroy();
		}

	}
}
