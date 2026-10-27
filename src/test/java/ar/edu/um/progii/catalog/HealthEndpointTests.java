package ar.edu.um.progii.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

// Un Redis inexistente simula la cátedra caída: el health no tiene que enterarse
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.data.redis.host=redis.invalid")
class HealthEndpointTests {

	@LocalServerPort
	private int port;

	@Test
	void showsOnlyStatusWithoutAuthentication() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
	}

}
