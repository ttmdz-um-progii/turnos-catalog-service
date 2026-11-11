package ar.edu.um.progii.catalog.sync.infrastructure.rest.adapter;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogCredentialsRejectedException;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InvalidCatalogSnapshotException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogSnapshotSource;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.CatalogSnapshotApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.ProblemApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.mapper.CatalogRestMapper;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Pide GET /api/synchronization/snapshot a la API de la cátedra (anexo §7) con el JWT técnico. Los errores se deciden
// por status y code, nunca por el texto (anexo §13): 401 y 403 son credenciales rechazadas; cualquier otro error, un
// timeout o una conexión caída, cátedra no disponible; y un cuerpo ilegible, incompleto o inconsistente, snapshot
// inválido. Solo la cátedra no disponible se reintenta, con una espera por reintento: las credenciales rechazadas y el
// snapshot inválido no se arreglan pidiendo de nuevo
@Slf4j
@Component
@RequiredArgsConstructor
public class RestCatalogSnapshotSourceAdapter implements CatalogSnapshotSource {

	private static final String SNAPSHOT_PATH = "/api/synchronization/snapshot";

	private final RestClient catalogRestClient;
	private final CatalogRestMapper catalogRestMapper;
	// Cuánto esperar antes de cada reintento: con 1, 2 y 4 s son cuatro pedidos como máximo
	@Value("${catedra.api.retry-delays}")
	private final List<Duration> retryDelays;

	@Override
	public CatalogSnapshot readSnapshot() {

		for (Duration retryDelay : retryDelays) {
			try {
				return requestSnapshot();
			} catch (CatalogSourceUnavailableException unavailableException) {
				log.warn("{}; retrying in {}", unavailableException.getMessage(), retryDelay);
				waitBeforeRetrying(retryDelay, unavailableException);
			}
		}
		// Último intento: si vuelve a fallar, la excepción sale tal cual
		return requestSnapshot();

	}

	private static void waitBeforeRetrying(Duration retryDelay,
			CatalogSourceUnavailableException unavailableException) {

		try {
			Thread.sleep(retryDelay);
		} catch (InterruptedException interruptedException) {
			// Se restaura el aviso de interrupción para que quien apaga el servicio lo vea
			Thread.currentThread().interrupt();
			throw new CatalogSourceUnavailableException("Interrupted while waiting to retry " + SNAPSHOT_PATH,
					unavailableException);
		}

	}

	private CatalogSnapshot requestSnapshot() {

		CatalogSnapshotApiResponse catalogSnapshotResponse;
		try {
			catalogSnapshotResponse = catalogRestClient.get()
					.uri(SNAPSHOT_PATH)
					.retrieve()
					.body(CatalogSnapshotApiResponse.class);
		} catch (RestClientResponseException errorResponseException) {
			throw translateErrorResponse(errorResponseException);
		} catch (ResourceAccessException connectionException) {
			throw new CatalogSourceUnavailableException("Could not reach " + SNAPSHOT_PATH, connectionException);
		} catch (RestClientException unreadableBodyException) {
			throw new InvalidCatalogSnapshotException("Unreadable snapshot: " + unreadableBodyException.getMessage(),
					unreadableBodyException);
		}

		if (catalogSnapshotResponse == null) {
			throw new InvalidCatalogSnapshotException("The snapshot came without a body");
		}
		try {
			return catalogRestMapper.toCatalogSnapshot(catalogSnapshotResponse);
		} catch (IllegalArgumentException invalidSnapshotException) {
			throw new InvalidCatalogSnapshotException("Invalid snapshot: " + invalidSnapshotException.getMessage(),
					invalidSnapshotException);
		}

	}

	private static RuntimeException translateErrorResponse(RestClientResponseException errorResponseException) {

		int status = errorResponseException.getStatusCode().value();
		String errorDescription = SNAPSHOT_PATH + " answered " + status + " with code "
				+ readErrorCode(errorResponseException);
		if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
			return new CatalogCredentialsRejectedException(errorDescription, errorResponseException);
		}
		return new CatalogSourceUnavailableException(errorDescription, errorResponseException);

	}

	// El code del cuerpo problem+json, o null si el error llegó sin cuerpo o con uno que no es problem+json
	private static String readErrorCode(RestClientResponseException errorResponseException) {

		try {
			ProblemApiResponse problemResponse = errorResponseException.getResponseBodyAs(ProblemApiResponse.class);
			return problemResponse == null ? null : problemResponse.code();
		} catch (RestClientException unreadableProblemException) {
			return null;
		}

	}
}
