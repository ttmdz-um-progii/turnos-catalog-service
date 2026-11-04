-- Tablas de la réplica del catálogo de la cátedra: categorías, profesionales y horarios semanales.
-- La clave primaria es el id que asigna la cátedra, sin secuencia propia: aplicar un cambio es un upsert natural
-- y el id es el mismo en todos los servicios.
-- Ninguna restricción es más estricta que la cátedra (sin CHECK de horarios ni largos máximos): si lo fuera,
-- un dato publicado por ella no entraría nunca y la sincronización quedaría trabada.

CREATE TABLE professional_category (
    id          BIGINT  PRIMARY KEY,
    name        TEXT    NOT NULL,
    description TEXT,
    enabled     BOOLEAN NOT NULL
);

CREATE TABLE professional (
    id          BIGINT  PRIMARY KEY,
    category_id BIGINT  NOT NULL REFERENCES professional_category (id),
    first_name  TEXT    NOT NULL,
    last_name   TEXT    NOT NULL,
    -- Nombre y apellido en minúsculas y sin acentos, para buscar sin distinguirlos. Lo calcula el servicio al escribir
    search_name TEXT    NOT NULL,
    enabled     BOOLEAN NOT NULL
);

CREATE INDEX idx_professional_category_id ON professional (category_id);

CREATE TABLE weekly_schedule (
    id                    BIGINT     PRIMARY KEY,
    professional_id       BIGINT     NOT NULL REFERENCES professional (id),
    -- Enum cerrado del contrato de la cátedra
    day_of_week           VARCHAR(9) NOT NULL
        CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time            TIME       NOT NULL,
    end_time              TIME       NOT NULL,
    slot_duration_minutes INTEGER    NOT NULL,
    enabled               BOOLEAN    NOT NULL
);

CREATE INDEX idx_weekly_schedule_professional_id_day_of_week ON weekly_schedule (professional_id, day_of_week);
