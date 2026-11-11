package ar.edu.um.progii.catalog.sync.domain.ports.out;

// Impide que dos sincronizaciones escriban la copia local a la vez. Quien ya lo tiene lo puede volver a tomar, así una
// sincronización puede llamar a otra sin trabarse; cada toma se libera con su propio release()
public interface SyncLock {

	// No espera: devuelve false si otra sincronización lo tiene
	boolean tryAcquire();

	void release();
}
