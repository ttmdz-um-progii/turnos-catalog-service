package ar.edu.um.progii.catalog.sync.infrastructure.kafka.listener;

import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.SyncOrigin;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import ar.edu.um.progii.catalog.sync.infrastructure.kafka.dto.CatalogUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// El disparador de CU-1 por aviso de la cátedra. El aviso no trae datos: solo dice que hay una versión nueva, y la
// sincronización la lee de la cátedra. Un mensaje a la vez (concurrencia 1), y el offset se confirma recién cuando la
// sincronización terminó (anexo §16)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.sync.notification-listener-enabled", havingValue = "true")
public class CatalogUpdatedEventListener {

	private final SyncCatalogUseCase syncCatalogUseCase;
	private final JsonMapper jsonMapper;

	// El offset se confirma siempre, también si falla: reintentar el mismo mensaje no arreglaría un JSON inválido, la
	// sincronización ya reintentó dentro de sus lecturas y la reconciliación vuelve a intentar sola
	@KafkaListener(topics = "${catedra.kafka.catalog-topic}", concurrency = "1")
	public void onCatalogUpdated(String eventJson, Acknowledgment acknowledgment) {

		try {
			CatalogUpdatedEvent catalogUpdatedEvent = jsonMapper.readValue(eventJson, CatalogUpdatedEvent.class);
			log.info("Catalog notification {} for version {}", catalogUpdatedEvent.eventId(),
					catalogUpdatedEvent.newVersion());
			syncCatalogUseCase.syncCatalog(SyncOrigin.notification(catalogUpdatedEvent.eventId()));
		} catch (JacksonException unreadableEventException) {
			log.warn("Ignored an unreadable catalog notification: {}", unreadableEventException.getOriginalMessage());
		} catch (SyncAlreadyRunningException alreadyRunningException) {
			log.info("Skipped the notification synchronization: another one is running");
		} catch (RuntimeException syncException) {
			log.warn("The notification synchronization failed: {}", syncException.getMessage());
		} finally {
			acknowledgment.acknowledge();
		}

	}
}
