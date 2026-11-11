package ar.edu.um.progii.catalog.sync.infrastructure.rest.dto;

// Cuerpo application/problem+json de un error de la cátedra (anexo §13). Solo se lee code: los errores se deciden por
// él, nunca por el texto de detail. Los rechazos 401 y 403 pueden llegar sin code
public record ProblemApiResponse(String code) {
}
