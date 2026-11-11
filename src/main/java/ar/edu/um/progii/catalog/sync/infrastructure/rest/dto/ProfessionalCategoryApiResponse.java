package ar.edu.um.progii.catalog.sync.infrastructure.rest.dto;

// Elemento de professionalCategories en el snapshot (anexo §7)
public record ProfessionalCategoryApiResponse(long id, String name, String description, boolean enabled) {
}
