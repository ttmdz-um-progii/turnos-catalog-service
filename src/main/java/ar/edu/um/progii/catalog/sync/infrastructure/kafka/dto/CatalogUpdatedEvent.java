package ar.edu.um.progii.catalog.sync.infrastructure.kafka.dto;

// Valor de un CatalogUpdated del topic catedra.catalog.{groupId} (anexo §15.3). Solo trae lo que se usa: los demás
// campos del evento se ignoran, como pide el anexo §15.1
public record CatalogUpdatedEvent(String eventId, Long newVersion) {
}
