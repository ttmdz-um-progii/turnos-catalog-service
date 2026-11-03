package ar.edu.um.progii.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Cada corrida de tests levanta su propio PostgreSQL, la misma versión que Compose
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {

		return new PostgreSQLContainer("postgres:18");

	}

	// Los tests no usan la cátedra: un Redis inexistente, aunque haya un .env local
	@Bean
	DynamicPropertyRegistrar catalogSourcesUnavailable() {

		return registry -> registry.add("spring.data.redis.host", () -> "redis.invalid");

	}

}
