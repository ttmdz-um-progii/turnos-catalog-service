package ar.edu.um.progii.catalog.sync.infrastructure.rest.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Cliente de la API REST de la cátedra: cada pedido lleva el JWT técnico y tiene sus propios timeouts. El token
// queda solo en el header; no se loguea ni entra en ningún mensaje
@Configuration
public class CatalogRestConfig {

	@Bean
	public RestClient catalogRestClient(@Value("${catedra.api.base-url}") String baseUrl,
			@Value("${catedra.api.token}") String token,
			@Value("${catedra.api.connect-timeout}") Duration connectTimeout,
			@Value("${catedra.api.read-timeout}") Duration readTimeout) {

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeaders(headers -> headers.setBearerAuth(token))
				.requestFactory(requestFactory)
				.build();

	}
}
