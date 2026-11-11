package ar.edu.um.progii.catalog.sync.infrastructure.rest.dto;

// Elemento de professionals en el snapshot (anexo §7)
public record ProfessionalApiResponse(long id, long categoryId, String firstName, String lastName, boolean enabled) {
}
