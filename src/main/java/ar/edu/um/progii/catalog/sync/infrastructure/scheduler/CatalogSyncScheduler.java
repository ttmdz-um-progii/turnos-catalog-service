package ar.edu.um.progii.catalog.sync.infrastructure.scheduler;

import ar.edu.um.progii.catalog.sync.application.exception.SyncAlreadyRunningException;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import ar.edu.um.progii.catalog.sync.domain.ports.in.SyncCatalogUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Los dos disparadores de CU-1 que no son Kafka: el arranque del servicio y la reconciliación periódica, que alcanza la
// última versión aunque se haya perdido un aviso. La primera reconciliación espera un intervalo entero para no
// pisarse con la del arranque
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalog.sync.automatic-triggers-enabled", havingValue = "true")
public class CatalogSyncScheduler {

	private final SyncCatalogUseCase syncCatalogUseCase;

	@EventListener(ApplicationReadyEvent.class)
	public void syncCatalogOnStartup() {

		syncCatalog(SyncTrigger.STARTUP);

	}

	@Scheduled(fixedDelayString = "${catalog.sync.reconcile-interval}", initialDelayString = "${catalog.sync.reconcile-interval}")
	public void reconcileCatalog() {

		syncCatalog(SyncTrigger.SCHEDULED);

	}

	// Una falla ya quedó registrada en sync_run y la próxima reconciliación vuelve a intentar. Acá solo se loguea: si
	// la excepción saliera del arranque, Spring cortaría el inicio del servicio
	private void syncCatalog(SyncTrigger trigger) {

		try {
			syncCatalogUseCase.syncCatalog(trigger);
		} catch (SyncAlreadyRunningException alreadyRunningException) {
			log.info("Skipped the {} synchronization: another one is running", trigger);
		} catch (RuntimeException syncException) {
			log.warn("The {} synchronization failed: {}", trigger, syncException.getMessage());
		}

	}
}
