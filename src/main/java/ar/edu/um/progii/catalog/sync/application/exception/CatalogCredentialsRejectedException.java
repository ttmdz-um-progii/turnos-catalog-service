package ar.edu.um.progii.catalog.sync.application.exception;

// La cátedra rechazó las credenciales técnicas del servicio, por vencidas o inválidas: reintentar no lo arregla
public class CatalogCredentialsRejectedException extends RuntimeException {

	public CatalogCredentialsRejectedException(String message) {

		super(message);

	}

	public CatalogCredentialsRejectedException(String message, Throwable cause) {

		super(message, cause);

	}
}
