package ar.edu.um.progii.catalog.sync.infrastructure.redis.dto;

// Valor de un campo del Hash catedra:sync:professionals (anexo §14.4)
public record ProfessionalRedisDto(long id, long categoryId, String firstName, String lastName, boolean enabled) {
}
