package ar.edu.um.progii.catalog.sync.application.exception;

import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;

// La copia local no se pudo escribir: no se guardó nada y quedó como estaba
public class ReplicaWriteFailedException extends SyncFailureException {

	public ReplicaWriteFailedException(String message, Throwable cause) {

		super(SyncErrorCode.REPLICA_WRITE_FAILED, message, cause);

	}
}
