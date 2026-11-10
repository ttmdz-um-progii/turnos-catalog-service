package ar.edu.um.progii.catalog.sync.infrastructure.redis.dto;

// Valor de catedra:sync:changes:{version} (anexo §14.5)
public record VersionChangesRedisDto(long version, ChangedIdsRedisDto changes) {
}
