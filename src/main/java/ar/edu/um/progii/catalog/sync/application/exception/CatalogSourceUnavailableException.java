package ar.edu.um.progii.catalog.sync.application.exception;

import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;

// La cátedra no respondió a tiempo o no publicó sus versiones: no se puede decidir nada y hay que reintentar después
public class CatalogSourceUnavailableException extends SyncFailureException {

	public CatalogSourceUnavailableException(String message) {

		super(SyncErrorCode.CATALOG_SOURCE_UNAVAILABLE, message, null);

	}

	public CatalogSourceUnavailableException(String message, Throwable cause) {

		super(SyncErrorCode.CATALOG_SOURCE_UNAVAILABLE, message, cause);

	}
}
