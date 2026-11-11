package ar.edu.um.progii.catalog.sync.infrastructure.lock.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

// Otra sincronización se simula con otro hilo: el lock solo deja pasar de nuevo al hilo que ya lo tiene
class InMemorySyncLockAdapterTest {

	private final InMemorySyncLockAdapter inMemorySyncLockAdapter = new InMemorySyncLockAdapter();

	@Test
	void letsTheSameSyncTakeItAgain() {

		assertThat(inMemorySyncLockAdapter.tryAcquire()).isTrue();
		assertThat(inMemorySyncLockAdapter.tryAcquire()).isTrue();

		// Queda tomado hasta que se libera cada toma
		inMemorySyncLockAdapter.release();
		assertThat(tryAcquireFromAnotherSync()).isFalse();
		inMemorySyncLockAdapter.release();
		assertThat(tryAcquireFromAnotherSync()).isTrue();

	}

	@Test
	void keepsOutAnotherSyncWhileItIsTaken() {

		assertThat(inMemorySyncLockAdapter.tryAcquire()).isTrue();

		assertThat(tryAcquireFromAnotherSync()).isFalse();

	}

	private boolean tryAcquireFromAnotherSync() {

		return CompletableFuture.supplyAsync(() -> inMemorySyncLockAdapter.tryAcquire()).join();

	}
}
