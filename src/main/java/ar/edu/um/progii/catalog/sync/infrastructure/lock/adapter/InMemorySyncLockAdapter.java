package ar.edu.um.progii.catalog.sync.infrastructure.lock.adapter;

import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncLock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

// Lock en memoria (decisión 17): alcanza porque Compose levanta una sola instancia del servicio, y no queda tomado
// tras un reinicio porque muere con el proceso. ReentrantLock deja que el mismo hilo lo tome otra vez, y cuenta las
// tomas: queda libre recién cuando cada una se liberó
@Component
public class InMemorySyncLockAdapter implements SyncLock {

	private final ReentrantLock syncReentrantLock = new ReentrantLock();

	@Override
	public boolean tryAcquire() {

		return syncReentrantLock.tryLock();

	}

	@Override
	public void release() {

		syncReentrantLock.unlock();

	}
}
