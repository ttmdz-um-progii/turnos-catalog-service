package ar.edu.um.progii.catalog.sync.application.exception;

// La cátedra no respondió a tiempo o no publicó sus versiones: no se puede decidir nada y hay que reintentar después
public class CatalogSourceUnavailableException extends RuntimeException {

	public CatalogSourceUnavailableException(String message) {

		super(message);

	}

	public CatalogSourceUnavailableException(String message, Throwable cause) {

		super(message, cause);

	}
}
