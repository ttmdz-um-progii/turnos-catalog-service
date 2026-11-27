package ar.edu.um.progii.catalog.shared.application.exception;

// La copia local nunca se sincronizó: no hay datos para responder, ni siquiera viejos
public class CatalogNotInitializedException extends RuntimeException {

	public CatalogNotInitializedException() {

		super("The catalog has not been synchronized yet");

	}
}
