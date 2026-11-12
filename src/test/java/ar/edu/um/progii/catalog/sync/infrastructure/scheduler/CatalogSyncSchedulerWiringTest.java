package ar.edu.um.progii.catalog.sync.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.progii.catalog.TestcontainersConfiguration;
import ar.edu.um.progii.catalog.sync.domain.model.SyncOrigin;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

// Con los disparadores encendidos, Spring llama solo al caso de uso: una vez al arrancar y después cada intervalo, sin
// ningún aviso de Kafka. El caso de uso real se reemplaza por uno que anota los disparadores; que la sincronización
// alcance la última versión lo prueba SyncCatalogUseCaseImplIntegrationTest
@Import({TestcontainersConfiguration.class, CatalogSyncSchedulerWiringTest.RecordingSyncConfig.class})
@SpringBootTest(properties = {"catalog.sync.automatic-triggers-enabled=true", "catalog.sync.reconcile-interval=1s"})
class CatalogSyncSchedulerWiringTest {

	// Anota los disparadores en el orden en que llegan, desde el hilo del arranque y el del temporizador
	static class RecordingSyncCatalogUseCase implements SyncCatalogUseCase {

		private final List<SyncTrigger> receivedTriggers = new CopyOnWriteArrayList<>();
		private final CountDownLatch twoSyncsReceived = new CountDownLatch(2);

		@Override
		public void syncCatalog(SyncOrigin syncOrigin) {

			receivedTriggers.add(syncOrigin.getTrigger());
			twoSyncsReceived.countDown();

		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RecordingSyncConfig {

		@Bean
		@Primary
		RecordingSyncCatalogUseCase recordingSyncCatalogUseCase() {

			return new RecordingSyncCatalogUseCase();

		}
	}

	@Autowired
	private RecordingSyncCatalogUseCase recordingSyncCatalogUseCase;

	@Test
	void syncsOnStartupAndThenReconcilesOnItsOwn() throws InterruptedException {

		boolean bothSyncsArrived = recordingSyncCatalogUseCase.twoSyncsReceived.await(10, TimeUnit.SECONDS);

		assertThat(bothSyncsArrived).isTrue();
		assertThat(recordingSyncCatalogUseCase.receivedTriggers.subList(0, 2)).containsExactly(SyncTrigger.STARTUP,
				SyncTrigger.SCHEDULED);

	}
}
