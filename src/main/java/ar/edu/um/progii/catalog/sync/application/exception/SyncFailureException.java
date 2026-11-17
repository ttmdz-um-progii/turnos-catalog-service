package ar.edu.um.progii.catalog.sync.application.exception;

import ar.edu.um.progii.catalog.sync.domain.model.SyncErrorCode;

// Una falla que la sincronización registra como FAILED. Cada subclase lleva su código, así quien la atrapa registra el
// motivo sin preguntar de qué tipo es
public abstract class SyncFailureException extends RuntimeException {

	private final SyncErrorCode errorCode;

	protected SyncFailureException(SyncErrorCode errorCode, String message, Throwable cause) {

		super(message, cause);
		this.errorCode = errorCode;

	}

	public SyncErrorCode getErrorCode() {

		return errorCode;

	}
}
