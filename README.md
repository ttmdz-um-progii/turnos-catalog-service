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

Completar las variables `DB_*` (por ejemplo `DB_HOST=localhost`, `DB_PORT=5432`) y `REDIS_HOST`/`REDIS_PORT` con los datos de la cátedra: Spring Boot exige un host de Redis válido al arrancar, aunque se conecta recién cuando lo usa. `REDIS_PASSWORD` y `KAFKA_BOOTSTRAP_SERVERS` pueden quedar vacías.

## Levantar la base

```bash
docker compose up -d
```

Levanta PostgreSQL con el puerto `DB_PORT` y un volumen persistente (`catalog-db-data`). Para borrar los datos: `docker compose down -v`.

## Tests y arranque

Con la base levantada:

```bash
./mvnw test
./mvnw spring-boot:run
```

Para comprobar que está conectado a la base:

```bash
curl localhost:8080/actuator/health
```

Responde `{"status":"UP"}` mientras la base esté accesible.
