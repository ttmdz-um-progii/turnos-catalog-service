package ar.edu.um.progii.catalog.sync.infrastructure.redis.dto;

// Valor de un campo del Hash catedra:sync:professional-categories (anexo §14.4)
public record ProfessionalCategoryRedisDto(long id, String name, String description, boolean enabled) {
}
