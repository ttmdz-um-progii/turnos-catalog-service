package ar.edu.um.progii.catalog.sync.application.exception;

// Aplicar los cambios dejaba una referencia rota en la copia local: no se guardó nada y el estado no se puede
// verificar, así que hay que reconstruir desde el snapshot
public class InconsistentChangesException extends RuntimeException {

	public InconsistentChangesException(String message, Throwable cause) {

		super(message, cause);

	}
}
