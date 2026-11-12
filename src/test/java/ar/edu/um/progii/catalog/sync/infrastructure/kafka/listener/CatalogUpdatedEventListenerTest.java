package ar.edu.um.progii.catalog.sync.infrastructure.kafka.listener;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.domain.model.SyncOrigin;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

class CatalogUpdatedEventListenerTest {

	private static final String CATALOG_UPDATED_VERSION_8 = "{\"eventId\":\"catalog-event-8-4f3b\",\"newVersion\":8}";

	// Anota con qué origen se lo llamó y, si se le pide, falla con la excepción indicada
	static class RecordingSyncCatalogUseCase implements SyncCatalogUseCase {

		private final List<SyncOrigin> receivedOrigins = new ArrayList<>();
		private RuntimeException exceptionToThrow;

		@Override
		public void syncCatalog(SyncOrigin syncOrigin) {

			receivedOrigins.add(syncOrigin);
			if (exceptionToThrow != null) {
				throw exceptionToThrow;
			}

		}
	}

	static class CountingAcknowledgment implements Acknowledgment {

		private int acknowledgements;

		@Override
		public void acknowledge() {

			acknowledgements++;

		}
	}

	private final RecordingSyncCatalogUseCase recordingSyncCatalogUseCase = new RecordingSyncCatalogUseCase();
	private final CountingAcknowledgment countingAcknowledgment = new CountingAcknowledgment();
	private final CatalogUpdatedEventListener catalogUpdatedEventListener = new CatalogUpdatedEventListener(
			recordingSyncCatalogUseCase, JsonMapper.builder().build());

	@Test
	void syncsWithTheEventIdAndThenConfirmsTheOffset() {

		catalogUpdatedEventListener.onCatalogUpdated(CATALOG_UPDATED_VERSION_8, countingAcknowledgment);

		assertThat(recordingSyncCatalogUseCase.receivedOrigins).hasSize(1);
		SyncOrigin notificationOrigin = recordingSyncCatalogUseCase.receivedOrigins.getFirst();
		assertThat(notificationOrigin.getTrigger()).isEqualTo(SyncTrigger.NOTIFICATION);
		assertThat(notificationOrigin.getEventId()).isEqualTo("catalog-event-8-4f3b");
		assertThat(countingAcknowledgment.acknowledgements).isEqualTo(1);

	}

	// La falla ya quedó registrada y la reconciliación va a reintentar: trabarse en el mensaje no agrega nada
	@Test
	void confirmsTheOffsetWhenTheSyncFails() {

		recordingSyncCatalogUseCase.exceptionToThrow = new CatalogSourceUnavailableException("Redis did not answer");

		catalogUpdatedEventListener.onCatalogUpdated(CATALOG_UPDATED_VERSION_8, countingAcknowledgment);

		assertThat(countingAcknowledgment.acknowledgements).isEqualTo(1);

	}

	// Un mensaje que no se puede leer nunca se va a poder leer: se confirma sin sincronizar
	@Test
	void confirmsAnUnreadableMessageWithoutSyncing() {

		catalogUpdatedEventListener.onCatalogUpdated("not json", countingAcknowledgment);

		assertThat(recordingSyncCatalogUseCase.receivedOrigins).isEmpty();
		assertThat(countingAcknowledgment.acknowledgements).isEqualTo(1);

	}

	@Test
	void confirmsAMessageWithoutEventIdWithoutSyncing() {

		catalogUpdatedEventListener.onCatalogUpdated("{\"newVersion\":8}", countingAcknowledgment);

		assertThat(recordingSyncCatalogUseCase.receivedOrigins).isEmpty();
		assertThat(countingAcknowledgment.acknowledgements).isEqualTo(1);

	}
}
