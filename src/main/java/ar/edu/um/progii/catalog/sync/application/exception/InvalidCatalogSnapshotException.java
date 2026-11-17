package ar.edu.um.progii.catalog.sync.application.exception;

import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;

// El snapshot de la cátedra no se puede aplicar: no se pudo leer o no cumple el modelo. El mensaje dice el motivo
public class InvalidCatalogSnapshotException extends SyncFailureException {

	public InvalidCatalogSnapshotException(String message) {

		super(SyncErrorCode.INVALID_CATALOG_SNAPSHOT, message, null);

	}

	public InvalidCatalogSnapshotException(String message, Throwable cause) {

		super(SyncErrorCode.INVALID_CATALOG_SNAPSHOT, message, cause);

	}
}
