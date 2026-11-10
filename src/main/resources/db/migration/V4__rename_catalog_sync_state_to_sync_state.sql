-- La tabla del estado de la sincronización pasa a llamarse como sync_run, sin el prefijo catalog: el módulo ya es sync
-- y la base es solo del servicio de catálogo.
-- Renombrar la tabla no cambia los nombres que PostgreSQL les dio a sus restricciones, así que se renombran a mano

ALTER TABLE catalog_sync_state RENAME TO sync_state;

-- Renombrar la clave primaria también renombra su índice
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_pkey TO sync_state_pkey;
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_id_check TO sync_state_id_check;
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_status_check TO sync_state_status_check;
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_id_not_null TO sync_state_id_not_null;
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_status_not_null TO sync_state_status_not_null;
ALTER TABLE sync_state RENAME CONSTRAINT catalog_sync_state_consecutive_failures_not_null
    TO sync_state_consecutive_failures_not_null;
