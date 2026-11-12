package ar.edu.um.progii.catalog.sync.infrastructure.persistence.adapter;

import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.ports.out.SyncRunRepository;
import ar.edu.um.progii.catalog.sync.infrastructure.persistence.repository.JpaSyncRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaSyncRunRepositoryAdapter implements SyncRunRepository {

	private static final List<SyncRunResult> COMPLETED_RUN_RESULTS = List.of(SyncRunResult.SUCCESS,
			SyncRunResult.NOOP);

	private final JpaSyncRunRepository jpaSyncRunRepository;

	@Override
	@Transactional(readOnly = true)
	public boolean existsCompletedRunForEvent(String eventId) {

		return jpaSyncRunRepository.existsByEventIdAndRunResultIn(eventId, COMPLETED_RUN_RESULTS);

	}
}
