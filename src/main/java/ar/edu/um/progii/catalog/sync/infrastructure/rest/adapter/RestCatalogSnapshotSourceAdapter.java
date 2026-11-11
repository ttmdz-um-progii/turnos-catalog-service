package ar.edu.um.progii.catalog.sync.infrastructure.rest.adapter;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogCredentialsRejectedException;
import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.InvalidCatalogSnapshotException;
import ar.edu.um.progii.catalog.sync.domain.model.CatalogSnapshot;
import ar.edu.um.progii.catalog.sync.domain.ports.out.CatalogSnapshotSource;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.CatalogSnapshotApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.dto.ProblemApiResponse;
import ar.edu.um.progii.catalog.sync.infrastructure.rest.mapper.CatalogRestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Pide GET /api/synchronization/snapshot a la API de la cátedra (anexo §7) con el JWT técnico. Los errores se deciden
// por status y code, nunca por el texto (anexo §13): 401 y 403 son credenciales rechazadas; cualquier otro error, un
// timeout o una conexión caída, cátedra no disponible; y un cuerpo ilegible, incompleto o inconsistente, snapshot
// inválido
@Component
@RequiredArgsConstructor
public class RestCatalogSnapshotSourceAdapter implements CatalogSnapshotSource {

	private static final String SNAPSHOT_PATH = "/api/synchronization/snapshot";

	private final RestClient catalogRestClient;
	private final CatalogRestMapper catalogRestMapper;

	@Override
	public CatalogSnapshot readSnapshot() {

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
