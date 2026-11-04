-- Tablas de la sincronización: el estado de la réplica y el historial de ejecuciones.
-- Sus valores los escribe solo este servicio, así que los enums llevan CHECK.

-- Una sola fila (id = 1) con el estado actual de la réplica
CREATE TABLE catalog_sync_state (
    id                   SMALLINT    PRIMARY KEY CHECK (id = 1),
    -- Versión de la cátedra que tiene aplicada la réplica. Null = nunca sincronizado
    applied_version      BIGINT,
    -- Última versión actual de la cátedra que se leyó, para informar el atraso sin consultarla
    remote_version       BIGINT,
    status               VARCHAR(16) NOT NULL CHECK (status IN ('EMPTY', 'IN_SYNC', 'DEGRADED', 'ERROR')),
    last_success_at      TIMESTAMPTZ,
    last_attempt_at      TIMESTAMPTZ,
    last_full_sync_at    TIMESTAMPTZ,
    consecutive_failures INTEGER     NOT NULL DEFAULT 0,
    last_error_code      TEXT,
    last_error_message   TEXT
);

INSERT INTO catalog_sync_state (id, status) VALUES (1, 'EMPTY');

-- Una fila por cada ejecución de la sincronización, con su disparador, modo y resultado
CREATE TABLE sync_run (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trigger_source   VARCHAR(16) NOT NULL CHECK (trigger_source IN ('STARTUP', 'NOTIFICATION', 'SCHEDULED', 'MANUAL')),
    -- Id del aviso de la cátedra que disparó la ejecución, si lo hubo
    event_id         TEXT,
    mode             VARCHAR(16) NOT NULL CHECK (mode IN ('FULL', 'INCREMENTAL')),
    from_version     BIGINT,
    to_version       BIGINT,
    result           VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS', 'NOOP', 'FAILED')),
    entities_applied INTEGER     NOT NULL DEFAULT 0,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ NOT NULL,
    error_code       TEXT,
    error_message    TEXT
);

-- Común y no único: un aviso duplicado se registra como NOOP y uno que falló se puede reintentar
CREATE INDEX idx_sync_run_event_id ON sync_run (event_id);
