#!/bin/bash
# Crea el usuario de la aplicación, con permisos solo sobre su propio esquema.
# PostgreSQL lo ejecuta como superusuario únicamente cuando el volumen está vacío.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$DB_USERNAME" \
  -v app_password="$DB_PASSWORD" \
  -v app_schema="$DB_SCHEMA" \
  -v app_db="$DB_NAME" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE SCHEMA :"app_schema" AUTHORIZATION :"app_user";
ALTER ROLE :"app_user" SET search_path = :"app_schema";

-- Solo se conecta a su base, ni siquiera a las que trae PostgreSQL
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres, template1 FROM PUBLIC;
GRANT CONNECT ON DATABASE :"app_db" TO :"app_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
