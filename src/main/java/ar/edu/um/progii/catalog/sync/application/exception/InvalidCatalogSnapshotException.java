package ar.edu.um.progii.catalog.sync.application.exception;

// El snapshot de la cátedra no se puede aplicar: no se pudo leer o no cumple el modelo. El mensaje dice el motivo
public class InvalidCatalogSnapshotException extends RuntimeException {

	public InvalidCatalogSnapshotException(String message) {

		super(message);

	}

	public InvalidCatalogSnapshotException(String message, Throwable cause) {

		super(message, cause);

	}
}
