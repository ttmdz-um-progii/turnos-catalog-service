package ar.edu.um.progii.catalog.sync.infrastructure.persistence.entity;

import java.time.Instant;

import ar.edu.um.progii.catalog.sync.domain.model.SyncMode;
import ar.edu.um.progii.catalog.sync.domain.model.SyncRunResult;
import ar.edu.um.progii.catalog.sync.domain.model.SyncTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "sync_run")
@NoArgsConstructor
public class SyncRunEntity {

	// Este id sí lo genera la base
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Enumerated(EnumType.STRING)
	private SyncTrigger triggerSource;
	private String eventId;
	@Enumerated(EnumType.STRING)
	private SyncMode mode;
	private Long fromVersion;
	private Long toVersion;
	// La columna se llama result, pero en el código se nombra el rol (regla 25)
	@Enumerated(EnumType.STRING)
	@Column(name = "result")
	private SyncRunResult runResult;
	private int entitiesApplied;
	private Instant startedAt;
	private Instant finishedAt;
	private String errorCode;
	private String errorMessage;
}
