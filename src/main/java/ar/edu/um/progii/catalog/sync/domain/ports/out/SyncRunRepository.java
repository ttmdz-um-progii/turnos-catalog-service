package ar.edu.um.progii.catalog.sync.domain.ports.out;

// Las ejecuciones ya registradas de la sincronización
public interface SyncRunRepository {

	// True si un aviso con ese eventId ya terminó en SUCCESS o NOOP. Uno que terminó en FAILED no cuenta, para que se
	// pueda reintentar
	boolean existsCompletedRunForEvent(String eventId);
}
