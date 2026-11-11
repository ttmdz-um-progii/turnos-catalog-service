package ar.edu.um.progii.catalog.sync.infrastructure.rest.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.progii.catalog.category.domain.model.ProfessionalCategory;
import ar.edu.um.progii.catalog.professional.domain.model.Professional;
import ar.edu.um.progii.catalog.professional.domain.model.WeeklySchedule;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogCredentialsRejectedException;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InvalidCatalogSnapshotException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.config.CatalogRestConfig;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.mapper.CatalogRestMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Corre contra un WireMock que hace de API de la cátedra, sin Spring ni red. El cliente se arma con la misma
// configuración que la aplicación, pero con un read timeout corto para probar el vencimiento rápido
class RestCatalogSnapshotSourceAdapterTest {

	private static final String SNAPSHOT_PATH = "/api/synchronization/snapshot";

	// El ejemplo del anexo (§7), con un segundo horario en HH:mm y un campo que el contrato no define
	private static final String ANNEX_SNAPSHOT_JSON = """
			{
			  "snapshotVersion": 7,
			  "generatedAt": "2026-09-10T13:44:59Z",
			  "unknownField": "ignored",
			  "professionalCategories": [
			    {"id": 1, "name": "Clinica medica", "description": "Atencion clinica general", "enabled": true,
			     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
			  ],
			  "professionals": [
			    {"id": 15, "categoryId": 1, "firstName": "Ana", "lastName": "Gómez", "enabled": true,
			     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"}
			  ],
			  "weeklySchedules": [
			    {"id": 21, "professionalId": 15, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
			     "slotDurationMinutes": 30, "enabled": true,
			     "createdAt": "2026-07-01T12:00:00Z", "updatedAt": "2026-09-10T13:40:00Z"},
			    {"id": 22, "professionalId": 15, "dayOfWeek": "TUESDAY", "startTime": "14:00", "endTime": "18:30",
			     "slotDurationMinutes": 20, "enabled": false}
			  ]
			}
			""";

	private WireMockServer catedraApiServer;
	private RestCatalogSnapshotSourceAdapter restCatalogSnapshotSourceAdapter;

	@BeforeEach
	void startTheCatedraApi() {

		catedraApiServer = new WireMockServer(wireMockConfig().dynamicPort());
		catedraApiServer.start();
		restCatalogSnapshotSourceAdapter = new RestCatalogSnapshotSourceAdapter(
				new CatalogRestConfig().catalogRestClient(catedraApiServer.baseUrl(), "test-token",
						Duration.ofSeconds(1), Duration.ofMillis(500)),
				new CatalogRestMapper());

	}

	@AfterEach
	void stopTheCatedraApi() {

		catedraApiServer.stop();

	}

	@Test
	void readsTheSnapshot() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(ANNEX_SNAPSHOT_JSON)));

		CatalogSnapshot catalogSnapshot = restCatalogSnapshotSourceAdapter.readSnapshot();

		assertThat(catalogSnapshot.getSnapshotVersion()).isEqualTo(7L);

		ProfessionalCategory professionalCategory = catalogSnapshot.getProfessionalCategories().getFirst();
		assertThat(professionalCategory.getId()).isEqualTo(1L);
		assertThat(professionalCategory.getName()).isEqualTo("Clinica medica");
		assertThat(professionalCategory.getDescription()).isEqualTo("Atencion clinica general");
		assertThat(professionalCategory.isEnabled()).isTrue();

		Professional professional = catalogSnapshot.getProfessionals().getFirst();
		assertThat(professional.getId()).isEqualTo(15L);
		assertThat(professional.getCategoryId()).isEqualTo(1L);
		assertThat(professional.getFirstName()).isEqualTo("Ana");
		assertThat(professional.getLastName()).isEqualTo("Gómez");
		assertThat(professional.getSearchName()).isEqualTo("ana gomez");
		assertThat(professional.isEnabled()).isTrue();

		assertThat(catalogSnapshot.getWeeklySchedules()).hasSize(2);
		WeeklySchedule mondaySchedule = catalogSnapshot.getWeeklySchedules().get(0);
		assertThat(mondaySchedule.getId()).isEqualTo(21L);
		assertThat(mondaySchedule.getProfessionalId()).isEqualTo(15L);
		assertThat(mondaySchedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(mondaySchedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(mondaySchedule.getEndTime()).isEqualTo(LocalTime.of(12, 0));
		assertThat(mondaySchedule.getSlotDurationMinutes()).isEqualTo(30);
		assertThat(mondaySchedule.isEnabled()).isTrue();
		WeeklySchedule tuesdaySchedule = catalogSnapshot.getWeeklySchedules().get(1);
		assertThat(tuesdaySchedule.getStartTime()).isEqualTo(LocalTime.of(14, 0));
		assertThat(tuesdaySchedule.getEndTime()).isEqualTo(LocalTime.of(18, 30));
		assertThat(tuesdaySchedule.isEnabled()).isFalse();

		catedraApiServer.verify(getRequestedFor(urlEqualTo(SNAPSHOT_PATH))
				.withHeader("Authorization", equalTo("Bearer test-token")));

	}

	@Test
	void rejectsTheCredentials() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(401)));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(CatalogCredentialsRejectedException.class)
				.hasMessageContaining("401");

	}

	@Test
	void failsWhenTheSourceIsUnavailable() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(503)
				.withHeader("Content-Type", "application/problem+json")
				.withBody("{\"status\":503,\"detail\":\"Database down\",\"code\":\"SERVICE_UNAVAILABLE\"}")));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(CatalogSourceUnavailableException.class)
				.hasMessageContaining("503")
				.hasMessageContaining("SERVICE_UNAVAILABLE");

	}

	@Test
	void failsWhenTheSourceTimesOut() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(ANNEX_SNAPSHOT_JSON)
				.withFixedDelay(2000)));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(CatalogSourceUnavailableException.class);

	}

	@Test
	void rejectsASnapshotWithoutACollection() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"snapshotVersion\":7,\"professionalCategories\":[],\"weeklySchedules\":[]}")));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(InvalidCatalogSnapshotException.class)
				.hasMessageContaining("Professionals are mandatory");

	}

	@Test
	void rejectsASnapshotWithABrokenReference() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"snapshotVersion\":7,\"professionalCategories\":[],\"weeklySchedules\":[],"
						+ "\"professionals\":[{\"id\":15,\"categoryId\":3,\"firstName\":\"Ana\",\"lastName\":\"Gomez\","
						+ "\"enabled\":true}]}")));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(InvalidCatalogSnapshotException.class)
				.hasMessageContaining("Professional 15 references the missing category 3");

	}

	@Test
	void rejectsAnUnreadableSnapshot() {

		catedraApiServer.stubFor(get(SNAPSHOT_PATH).willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"snapshotVersion\":7,\"professionals\":[")));

		assertThatThrownBy(() -> restCatalogSnapshotSourceAdapter.readSnapshot())
				.isInstanceOf(InvalidCatalogSnapshotException.class);

	}
}
