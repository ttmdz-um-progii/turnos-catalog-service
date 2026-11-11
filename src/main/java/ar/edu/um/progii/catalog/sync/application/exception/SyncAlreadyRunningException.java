package ar.edu.um.progii.catalog.sync.application.exception;

// Otra sincronización está escribiendo la copia local: esta no hace nada, porque la que corre ya va a llegar a la
// última versión
public class SyncAlreadyRunningException extends RuntimeException {

	public SyncAlreadyRunningException(String message) {

		super(message);

	}
}
