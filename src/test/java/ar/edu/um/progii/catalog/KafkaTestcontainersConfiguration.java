package ar.edu.um.progii.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

// Un Kafka propio en lugar del de la cátedra. Va aparte de TestcontainersConfiguration para que solo lo levanten los
// tests que lo usan
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {

		return new KafkaContainer("apache/kafka:4.0.0");

	}

}
