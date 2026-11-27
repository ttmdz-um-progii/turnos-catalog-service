package ar.edu.um.progii.catalog.shared.infrastructure.web.exception;

import ar.edu.um.progii.catalog.shared.application.exception.CatalogNotInitializedException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;

// Traduce los errores que no son de ningún módulo. Va último: el handler de un módulo, si tiene un método para la
// excepción, gana antes que el 500 de acá. La clase base ya responde los errores de Spring (ruta inexistente, método
// no permitido, parámetro faltante o mal tipado) con su status
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	// Todo 400 de Spring es un parámetro inválido
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
		// Spring devuelve null si ya empezó a escribir la respuesta
		if (response != null && status.isSameCodeAs(HttpStatus.BAD_REQUEST)
				&& response.getBody() instanceof ProblemDetail problem) {
			problem.setProperty("code", "VALIDATION_ERROR");
		}
		return response;

	}

	// Un @RequestParam o @PathVariable que no cumple su restricción: el detail dice cuál y por qué
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		List<String> invalidParameters = new ArrayList<>();
		for (ParameterValidationResult parameterResult : exception.getParameterValidationResults()) {
			String parameterName = parameterResult.getMethodParameter().getParameterName();
			for (MessageSourceResolvable parameterError : parameterResult.getResolvableErrors()) {
				invalidParameters.add(parameterName + ": " + parameterError.getDefaultMessage());
			}
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, String.join(", ", invalidParameters));
		return handleExceptionInternal(exception, problem, headers, status, request);

	}

	@ExceptionHandler(CatalogNotInitializedException.class)
	public ProblemDetail handleCatalogNotInitialized(CatalogNotInitializedException exception) {

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				exception.getMessage());
		problem.setProperty("code", "CATALOG_NOT_INITIALIZED");
		return problem;

	}

	// El mensaje puede traer un error de la cátedra o un dato interno: va solo al log, nunca a la respuesta
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception exception) {

		log.error("Unexpected error handling the request", exception);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		problem.setProperty("code", "INTERNAL_ERROR");
		return problem;

	}
}
