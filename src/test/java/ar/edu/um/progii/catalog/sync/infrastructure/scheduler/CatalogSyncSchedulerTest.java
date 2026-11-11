package ar.edu.um.progii.catalog.sync.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.sync.application.exception.CatalogSourceUnavailableException;
import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogSyncSchedulerTest {

	// Anota con qué disparador se lo llamó y, si se le pide, falla con la excepción indicada
	static class RecordingSyncCatalogUseCase implements SyncCatalogUseCase {

		private final List<SyncTrigger> receivedTriggers = new ArrayList<>();
		private RuntimeException exceptionToThrow;

		@Override
		public void syncCatalog(SyncTrigger trigger) {

			receivedTriggers.add(trigger);
			if (exceptionToThrow != null) {
				throw exceptionToThrow;
			}

		}
	}

	private final RecordingSyncCatalogUseCase recordingSyncCatalogUseCase = new RecordingSyncCatalogUseCase();
	private final CatalogSyncScheduler catalogSyncScheduler = new CatalogSyncScheduler(recordingSyncCatalogUseCase);

	@Test
	void syncsWithTheStartupTriggerWhenTheServiceStarts() {

		catalogSyncScheduler.syncCatalogOnStartup();

		assertThat(recordingSyncCatalogUseCase.receivedTriggers).containsExactly(SyncTrigger.STARTUP);

	}

	@Test
	void syncsWithTheScheduledTriggerWhenItReconciles() {

		catalogSyncScheduler.reconcileCatalog();

		assertThat(recordingSyncCatalogUseCase.receivedTriggers).containsExactly(SyncTrigger.SCHEDULED);

	}

	// Si la falla saliera del arranque, el servicio no levantaría
	@Test
	void keepsAFailedSyncFromStoppingTheStartup() {

		recordingSyncCatalogUseCase.exceptionToThrow = new CatalogSourceUnavailableException("Redis did not answer");

		catalogSyncScheduler.syncCatalogOnStartup();

		assertThat(recordingSyncCatalogUseCase.receivedTriggers).containsExactly(SyncTrigger.STARTUP);

	}

	@Test
	void skipsTheReconciliationWhileAnotherSyncIsRunning() {

		recordingSyncCatalogUseCase.exceptionToThrow = new SyncAlreadyRunningException("Another sync is running");

		catalogSyncScheduler.reconcileCatalog();

		assertThat(recordingSyncCatalogUseCase.receivedTriggers).containsExactly(SyncTrigger.SCHEDULED);

	}
}
