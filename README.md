# turnos-catalog-service

Servicio Spring Boot que mantiene una réplica local del catálogo de profesionales y horarios, sincronizada con la cátedra mediante snapshot REST, Redis y Kafka. Las búsquedas se responden desde su propia base PostgreSQL, así que el servicio sigue funcionando aunque la cátedra no esté accesible.

Sigue una arquitectura hexagonal sin capa de service: los adaptadores de entrada (controladores, listener de Kafka, temporizador) usan directamente los puertos de entrada, y cada tecnología externa es un adaptador en `infrastructure` del módulo que la usa.

## Requisitos

- JDK 25
- Docker (con Docker Compose)

Maven no hace falta: el proyecto trae el wrapper (`./mvnw`).

## Configuración

Toda la configuración llega por variables de entorno. Para desarrollo se leen del archivo `.env` en la raíz, que no se versiona:

```bash
cp .env.example .env
```

Completar:

- `POSTGRES_USER` / `POSTGRES_PASSWORD`: superusuario del contenedor de PostgreSQL. Solo se usa para inicializar la base.
- `DB_*`: conexión de la aplicación (por ejemplo `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=catalog`, `DB_SCHEMA=catalog`). `DB_USERNAME` tiene que ser distinto de `POSTGRES_USER`: es un usuario propio, con permisos solo sobre el esquema `DB_SCHEMA`, que crea `docker/postgres/init/01-app-user.sh`.
- `APP_PORT`: puerto donde Compose publica el servicio (por ejemplo `8080`).
- `REDIS_HOST` / `REDIS_PORT`: datos de la cátedra. Spring Boot exige un host de Redis válido al arrancar, aunque se conecta recién cuando lo usa. `REDIS_PASSWORD` y `KAFKA_BOOTSTRAP_SERVERS` pueden quedar vacías.

Redis y Kafka son de la cátedra: no se levantan en este Compose.

## Levantar todo con Docker Compose

```bash
docker compose up --build
```

Levanta PostgreSQL y, cuando la base está lista, el servicio (que se compila dentro de la imagen). Comprobar que responde:

```bash
curl localhost:8080/actuator/health
```

Responde `{"status":"UP"}` mientras la base esté accesible y `{"status":"DOWN"}` (con código 503) si no lo está. Redis y Kafka de la cátedra no cuentan: sin ellos el servicio sigue respondiendo con su copia local. Compose usa este mismo endpoint como healthcheck, así que `docker compose ps` muestra el servicio como `healthy` o `unhealthy`.

Dentro de Compose el servicio se conecta a `postgres:5432`; el resto de la configuración sale del mismo `.env`.

El usuario de la aplicación se crea solo la primera vez, con el volumen vacío. Si se cambian las credenciales o el esquema, hay que borrar la base y volver a levantarla:

```bash
docker compose down -v
```

## Desarrollo local con Maven

Levantar solo la base y correr el servicio con el wrapper, con el mismo `.env`:

```bash
docker compose up -d postgres
./mvnw test
./mvnw spring-boot:run
```

## Migraciones

El esquema de la base lo crea y lo modifica Flyway: al arrancar, el servicio aplica las migraciones pendientes de `src/main/resources/db/migration/` y anota cada una en la tabla `flyway_schema_history`. Hibernate solo valida que las entidades coincidan con las tablas.

Una migración ya aplicada no se edita, porque Flyway detecta el cambio y el servicio no arranca. Cada cambio de esquema va en un archivo nuevo con la versión siguiente, por ejemplo `V2__create_professional_table.sql`.
