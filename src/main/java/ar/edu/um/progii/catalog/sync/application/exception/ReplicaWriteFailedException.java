package ar.edu.um.progii.catalog.sync.application.exception;

// La copia local no se pudo escribir: no se guardó nada y quedó como estaba
public class ReplicaWriteFailedException extends RuntimeException {

	public ReplicaWriteFailedException(String message, Throwable cause) {

		super(message, cause);

	}
}
