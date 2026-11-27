package ar.edu.um.progii.catalog.sync.application.exception;

import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;

// La cátedra rechazó las credenciales técnicas del servicio, por vencidas o inválidas: reintentar no lo arregla
public class CatalogCredentialsRejectedException extends SyncFailureException {

	public CatalogCredentialsRejectedException(String message) {

		super(SyncErrorCode.CATALOG_CREDENTIALS_REJECTED, message, null);

	}

	public CatalogCredentialsRejectedException(String message, Throwable cause) {

		super(SyncErrorCode.CATALOG_CREDENTIALS_REJECTED, message, cause);

	}
}
