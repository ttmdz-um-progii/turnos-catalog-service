# Arquitectura hexagonal del servicio

Este es el patrón obligatorio para todo el código de este repositorio. Está escrito para que lo siga un agente: primero las reglas, después dónde va cada pieza, las recetas y las plantillas, y al final cómo verificar.

> **Los nombres de módulos y clases de este documento son ilustrativos.** `Xxx`, `Yyy`, `xxx` e `yyy` representan cualquier concepto. Qué módulos tiene el servicio y cómo se llaman se decide en cada issue, no acá.

## 0. Cuándo leer este documento

- **Siempre** antes de crear, mover, renombrar o modificar un archivo bajo `src/main/java`, `src/test/java` o `src/main/resources/db/migration`.
- Vale igual si el módulo es nuevo o si solo se agrega un archivo a uno existente.
- Si lo que hay que hacer no está cubierto acá, **se pregunta antes de inventar** una carpeta, un sufijo o una excepción a una regla.

## 1. Vocabulario

| Término | Significado |
| --- | --- |
| `<base>` | Paquete raíz: `ar.edu.um.progii.catalog` (ruta `src/main/java/ar/edu/um/progii/catalog`) |
| Módulo | Carpeta de un concepto bajo `<base>`, con sus tres capas: `<base>/xxx/{domain,application,infrastructure}` |
| `Xxx` / `xxx` / `xxxs` | Concepto en PascalCase singular, camelCase singular y plural (para rutas HTTP y tablas) |
| Puerto de entrada | Interfaz en `domain/ports/in`: una operación que el módulo ofrece |
| Puerto de salida | Interfaz en `domain/ports/out`: algo que el módulo necesita del exterior (base, REST, Redis) |
| Adaptador de entrada | Código de afuera que llama a un puerto de entrada: controlador, listener de Kafka, tarea `@Scheduled` |
| Adaptador de salida | Implementación de un puerto de salida con una tecnología concreta |

## 2. Reglas obligatorias

Cada regla va con su motivo. Romper una es un error, aunque compile.

**Dependencias**

1. **NUNCA** importar `org.springframework.*` ni `jakarta.*` en `domain`. El centro no depende de tecnología. Lombok y `java.*` sí están permitidos.
2. **NUNCA** importar nada de `.application.` ni de `.infrastructure.` desde `domain`. Las dependencias apuntan hacia adentro.
3. **NUNCA** importar `.infrastructure.` desde `application`. De Spring, la aplicación solo usa `org.springframework.stereotype.Component` y, cuando una operación lo necesita, `org.springframework.transaction.annotation.Transactional`.
4. **NUNCA** nombrar una clase `...UseCaseImpl` fuera de `application/usecases` ni de sus tests. Afuera se inyecta siempre la interfaz del puerto.
5. Entre módulos: `yyy` solo puede usar `xxx.domain.model` y `xxx.domain.ports.in`. **NUNCA** el `application`, el `infrastructure` ni el `domain.ports.out` de otro módulo (sección 3).

**Camino de una llamada**

6. **NUNCA** crear `application/service` ni una clase `XxxService` como fachada (decisión 14). El camino es fijo: adaptador de entrada → puerto de entrada → `UseCaseImpl` → puerto de salida → adaptador de salida.
7. Los adaptadores de entrada (controlador, listener, temporizador) **solo** reciben puertos de entrada y sus propios mappers. **NUNCA** un puerto de salida ni un repositorio JPA.

**Ausencia y errores**

8. Los puertos de entrada **NUNCA** devuelven `Optional`. Si lo pedido no existe, el `UseCaseImpl` lanza `XxxNotFoundException`. Así todos los adaptadores de entrada reciben la misma regla.
9. Los puertos de salida **SÍ** devuelven `Optional` (o `boolean` al borrar). La persistencia solo informa "no hay" y no decide nada.
10. Las excepciones de negocio van en `application/exception`, extienden `RuntimeException` y se llaman `<Motivo>Exception`.
11. Un `@RestControllerAdvice(assignableTypes = XxxController.class)` por módulo traduce las excepciones a `ProblemDetail` (`application/problem+json`) con una propiedad `code`. **NUNCA** un `try/catch` en un controlador.
12. Los errores que llegan de la cátedra se deciden por su `code`, **NUNCA** por el texto del mensaje.

**Modelo de dominio**

13. El modelo valida sus invariantes en el constructor y lanza `IllegalArgumentException`. Lleva `@Getter` y `@Builder` **sobre el constructor**, para que `builder()` también pase por la validación.
14. **NUNCA** `@Setter`, `@Data` ni `@NoArgsConstructor` en `domain`. Los cambios de estado se hacen con métodos con nombre de negocio (`changeXxx`, `disable`).
15. Una regla de negocio sin tecnología (cálculos, decisiones) va en `domain/model`, como clase o método, y se prueba con un test unitario puro. El `UseCaseImpl` la usa para orquestar los puertos; no la reescribe.

**Infraestructura**

16. Inyección **siempre** por constructor: campos `private final` y `@RequiredArgsConstructor`. **NUNCA** `@Autowired` sobre un campo en `src/main`.
17. Los mappers se escriben a mano, con una guarda de `null` al principio y construcción con `builder()`. **NUNCA** MapStruct ni ModelMapper.
18. Los métodos del adaptador de persistencia llevan `@Transactional` (`readOnly = true` en las lecturas). `update` carga la entidad existente y le cambia solo los campos que corresponden.
    - 18b. Para escrituras masivas con id externo, el adaptador usa SQL con `JdbcTemplate` (`INSERT ... ON CONFLICT (id) DO UPDATE`) en lugar de `save()` de JPA (decisión 18). Se llama `JdbcXxxWriterAdapter`, va en `infrastructure/persistence/adapter/` e implementa un puerto `out` del módulo (`XxxWriter`). El resto de las reglas no cambia.
19. Si el id lo asigna un sistema externo (por ejemplo, la cátedra), la entidad lleva `@Id` **sin** `@GeneratedValue`. Si lo genera la base, `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
20. Las validaciones de Bean Validation (`@NotBlank`, `@NotNull`…) van **solo** en los DTO de request. El dominio valida igual con sus propias reglas.
21. Credenciales, tokens y URLs **solo** en `application.properties` como `${VARIABLE}`. **NUNCA** hardcodeados.

**Base de datos**

22. El esquema lo crea **solo** Flyway. Nada de `ddl-auto=create` ni `update`.
23. **NUNCA** editar una migración ya commiteada: cada cambio va en un archivo `V<n>__<descripcion_en_snake_case>.sql` nuevo, con `<n>` igual al último más uno.

**Idioma**

24. Código, clases, tablas, columnas y endpoints en inglés. Comentarios y mensajes de commit en español.

## 3. Organización en módulos

- **Un módulo por concepto** del negocio, como carpeta directa bajo `<base>`: `<base>/xxx/`, `<base>/yyy/`.
- Cada módulo tiene sus tres capas, y solo crea las subcarpetas que usa. **NUNCA** se crean carpetas vacías "por si acaso".
- Lo que no es de ningún módulo:
  - La clase `@SpringBootApplication` queda en `<base>`.
  - Lo transversal (seguridad, errores globales, correlation id, configuración de Spring global como `@EnableScheduling`) va en el módulo **`<base>/shared/`**, con las mismas capas y carpetas por tecnología. `shared` no tiene lógica de negocio.

**¿Módulo nuevo o parte de uno existente?**

| Si la pieza nueva… | Entonces |
| --- | --- |
| Es otra operación sobre un concepto que ya tiene módulo | Va en ese módulo (receta B) |
| Es un concepto con identidad propia, su tabla y sus operaciones | Módulo nuevo (receta A) |
| Solo existe como parte de otro concepto y nunca se consulta sola | Es un modelo más dentro del módulo de ese concepto |
| No está claro | Preguntar antes de crear carpetas |

**Dependencias entre módulos**

```text
yyy.application  ──puede usar──▶  xxx.domain.model
                                  xxx.domain.ports.in
yyy.*            ──NUNCA──▶       xxx.domain.ports.out, xxx.application, xxx.infrastructure
cualquier módulo ──puede usar──▶  shared.*
shared.*         ──NUNCA──▶       ningún otro módulo
```

- Si `yyy` necesita leer o modificar algo de `xxx`, lo pide por un puerto de entrada de `xxx`. Si ese puerto no existe, se crea en `xxx` (receta B).
- **NUNCA** dos módulos que se usen mutuamente (ciclo). Si aparece uno, preguntar.
- **Cada tabla tiene un único módulo escritor.** Otro módulo puede leer esa misma tabla con su propia entidad JPA, pero solo lectura, y nunca importando la entidad del escritor. Qué módulo escribe cada tabla lo fija el diseño del servicio.
- Si una operación tiene que escribir en varios módulos dentro de **una misma transacción**, preguntar antes de implementarla: es una decisión de diseño.

## 4. Estructura canónica de un módulo

```text
<base>/xxx/
├── domain/
│   ├── model/              Xxx.java                       (+ reglas de negocio puras)
│   └── ports/
│       ├── in/             <Verbo>XxxUseCase.java         uno por operación
│       └── out/            XxxRepository.java             (+ XxxSource, XxxPublisher…)
├── application/
│   ├── usecases/           <Verbo>XxxUseCaseImpl.java     uno por puerto de entrada
│   └── exception/          XxxNotFoundException.java      (+ <Motivo>Exception)
└── infrastructure/
    ├── web/                adaptador de entrada HTTP
    │   ├── controller/     XxxController.java
    │   ├── dto/            XxxRequest.java, XxxResponse.java
    │   ├── mapper/         XxxDtoMapper.java
    │   └── exception/      XxxExceptionHandler.java
    ├── persistence/        adaptador de salida a la base propia
    │   ├── entity/         XxxEntity.java
    │   ├── repository/     JpaXxxRepository.java
    │   ├── mapper/         XxxMapper.java
    │   └── adapter/        JpaXxxRepositoryAdapter.java   (o JdbcXxxWriterAdapter, regla 18b)
    ├── rest/               adaptador de salida a una API externa
    │   ├── dto/            XxxApiResponse.java
    │   ├── mapper/         XxxRestMapper.java
    │   ├── config/         XxxRestConfig.java             (bean de RestClient, si hace falta)
    │   └── adapter/        RestXxxSourceAdapter.java
    ├── redis/              adaptador de salida a Redis
    │   ├── mapper/         XxxRedisMapper.java
    │   └── adapter/        RedisXxxSourceAdapter.java
    ├── kafka/              adaptador de entrada desde Kafka
    │   ├── dto/            XxxEvent.java
    │   └── listener/       XxxEventListener.java
    └── scheduler/          adaptador de entrada por temporizador
        └── XxxScheduler.java
```

Solo existen las carpetas de las tecnologías que el módulo usa. Para una tecnología nueva se agrega una carpeta hermana con su nombre, con subcarpetas por rol (`dto`, `mapper`, `config`, `adapter`, `listener`, `task`), y antes se pregunta si conviene sumarla a este documento.

## 5. Tabla de piezas

| Pieza | Carpeta | Nombre | Anotaciones | Puede depender de |
| --- | --- | --- | --- | --- |
| Modelo | `domain/model` | `Xxx` | `@Getter`, `@Builder` en el constructor | `java.*`, Lombok, otros modelos |
| Puerto de entrada | `domain/ports/in` | `<Verbo>XxxUseCase`, un método `<verbo>Xxx(...)` | ninguna | modelo |
| Puerto de salida (base) | `domain/ports/out` | `XxxRepository` | ninguna | modelo |
| Puerto de salida (otro) | `domain/ports/out` | `XxxSource` (lee de afuera), `XxxWriter` (escritura masiva), `XxxPublisher` (publica) | ninguna | modelo |
| Caso de uso | `application/usecases` | `<Verbo>XxxUseCaseImpl` | `@Component`, `@RequiredArgsConstructor` | puertos `in` y `out` del módulo, modelo, puertos `in` de otros módulos |
| Excepción | `application/exception` | `XxxNotFoundException`, `<Motivo>Exception` | ninguna | `java.*` |
| Controlador | `infrastructure/web/controller` | `XxxController` | `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor` | puertos `in`, `XxxDtoMapper`, DTO |
| DTO web | `infrastructure/web/dto` | `XxxRequest` (sin id, con validaciones), `XxxResponse` (con id) | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` | `jakarta.validation` |
| Mapper web | `infrastructure/web/mapper` | `XxxDtoMapper`: `toDomain`, `toResponse` | `@Component` | modelo, DTO |
| Handler de errores | `infrastructure/web/exception` | `XxxExceptionHandler` | `@RestControllerAdvice(assignableTypes = XxxController.class)` | excepciones del módulo |
| Entidad JPA | `infrastructure/persistence/entity` | `XxxEntity` | `@Entity @Table` + Lombok como el DTO | `jakarta.persistence` |
| Repositorio Spring Data | `infrastructure/persistence/repository` | `JpaXxxRepository extends JpaRepository<XxxEntity, Long>` | ninguna | entidad |
| Mapper de persistencia | `infrastructure/persistence/mapper` | `XxxMapper`: `toEntity`, `toDomainModel` | `@Component` | modelo, entidad |
| Adaptador de persistencia | `infrastructure/persistence/adapter` | `JpaXxxRepositoryAdapter implements XxxRepository` | `@Component`, `@RequiredArgsConstructor`, `@Transactional` por método | puerto `out`, repositorio JPA, mapper |
| Adaptador de escritura masiva | `infrastructure/persistence/adapter` | `JdbcXxxWriterAdapter implements XxxWriter` | `@Component`, `@RequiredArgsConstructor`, `@Transactional` por método | puerto `out`, `JdbcTemplate` |
| Adaptador REST externo | `infrastructure/rest/adapter` | `RestXxxSourceAdapter implements XxxSource` | `@Component`, `@RequiredArgsConstructor` | puerto `out`, `RestClient`, DTO y mapper REST |
| Adaptador Redis | `infrastructure/redis/adapter` | `RedisXxxSourceAdapter implements XxxSource` | `@Component`, `@RequiredArgsConstructor` | puerto `out`, cliente de Redis, mapper |
| Listener de Kafka | `infrastructure/kafka/listener` | `XxxEventListener` | `@Component`, `@RequiredArgsConstructor`, `@KafkaListener` en el método | puertos `in`, DTO del evento |
| Tarea programada | `infrastructure/scheduler` | `XxxScheduler` | `@Component`, `@RequiredArgsConstructor`, `@Scheduled` en el método | puertos `in` |
| Migración | `src/main/resources/db/migration` | `V<n>__<descripcion>.sql` | — | — |

## 6. Recetas

Se trabaja siempre **de adentro hacia afuera**: dominio → aplicación → infraestructura → migración → tests. Empezar por la entidad JPA termina en una entidad disfrazada de dominio.

### A. Crear un módulo nuevo

1. Definir: nombre del concepto (inglés, singular), campos y tipos (`BigDecimal` para dinero), invariantes, operaciones (cada una es un puerto `in`), qué necesita del exterior (cada cosa es un puerto `out`) y quién asigna el id.
2. `domain/model/Xxx.java` (plantilla 7.1).
3. `domain/ports/out/XxxRepository.java` con **solo** los métodos que van a usar los casos de uso (7.2).
4. Un `domain/ports/in/<Verbo>XxxUseCase.java` por operación (7.3).
5. `application/exception/XxxNotFoundException.java`, si alguna operación puede no encontrar lo pedido (7.4).
6. Un `application/usecases/<Verbo>XxxUseCaseImpl.java` por puerto `in` (7.5).
7. Persistencia: entidad, repositorio JPA, mapper y adaptador (7.6).
8. Migración Flyway nueva con la tabla y sus índices (7.9).
9. Si el módulo se expone por HTTP: DTO, mapper web, controlador y handler (7.7).
10. Tests (sección 8) y checklist (sección 9).

### B. Agregar una operación a un módulo existente

1. Leer el módulo entero antes de tocar nada: modelo, puertos y `Impl` que ya existen.
2. Si la operación necesita una regla nueva del concepto, agregarla al **modelo** como método con nombre de negocio.
3. Nuevo `domain/ports/in/<Verbo>XxxUseCase.java`. **No** sumarle un método a un puerto `in` que ya existe: es una interfaz por operación.
4. Si necesita algo nuevo del exterior, sumar el método al puerto `out` que corresponda (o crear un puerto nuevo) y después implementarlo en **su** adaptador.
5. Nuevo `application/usecases/<Verbo>XxxUseCaseImpl.java`.
6. Exponerla desde el adaptador de entrada que corresponda: inyectar el puerto `in` nuevo en el controlador, el listener o la tarea, y agregar el método.
7. Si aparece una excepción nueva, manejarla en el `XxxExceptionHandler` del módulo.
8. Test unitario del `Impl` con un fake del puerto `out` (8.1).

### C. Agregar un adaptador de otra tecnología

**De salida (REST externo, Redis):**

1. El puerto va primero: `domain/ports/out/XxxSource.java`, con tipos del dominio en parámetros y retornos. **NUNCA** DTO de la tecnología en la firma.
2. `infrastructure/<tecnologia>/dto/` con la forma exacta de lo que devuelve el sistema externo.
3. `infrastructure/<tecnologia>/mapper/` de DTO externo a modelo de dominio.
4. `infrastructure/<tecnologia>/adapter/<Tecnologia>XxxSourceAdapter implements XxxSource`. Traduce los errores de la tecnología a excepciones del módulo (en `application/exception`) decididas por el `code`.
5. Las URLs y las credenciales salen de propiedades `${VARIABLE}` (regla 21). Si hace falta un bean de cliente, va en `infrastructure/<tecnologia>/config/`.
6. Test de integración del adaptador contra un doble de la tecnología (WireMock para REST, Testcontainers para Redis).

**De entrada (listener de Kafka, `@Scheduled`):**

1. Llaman a un puerto `in` que ya existe, el mismo que usaría un controlador. **NUNCA** contienen lógica de negocio.
2. El listener deserializa a `infrastructure/kafka/dto/XxxEvent`, lo traduce y llama al puerto. Es idempotente, y el offset se confirma después de persistir.
3. La tarea programada solo dispara el puerto; el intervalo sale de una propiedad.

### D. Agregar o cambiar un campo persistido

Orden: modelo (campo + validación) → entidad → `XxxMapper` (las dos direcciones) → adaptador (`update` copia el campo nuevo) → migración **nueva** `V<n>__add_<campo>_to_<tabla>.sql` → DTO y `XxxDtoMapper`, si se expone → tests que cubran el campo.

### E. Regla de negocio pura

1. Clase o método en `domain/model`, sin Spring, sin puertos y sin estado oculto: recibe datos y devuelve una decisión o un valor.
2. Test unitario puro, uno por caso de la regla, sin Spring ni base.
3. El `UseCaseImpl` lee los datos por los puertos `out`, llama a la regla y actúa según lo que devuelve.

## 7. Plantillas

Son ilustrativas: los campos `description` y `price` se reemplazan por los del concepto. Indentación con tabs, como el resto del repo.

### 7.1 Modelo

```java
package ar.edu.um.progii.catalog.xxx.domain.model;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
public class Xxx {

	private final Long id;
	private String description;
	private BigDecimal price;

	@Builder
	public Xxx(Long id, String description, BigDecimal price) {
		this.id = id;
		this.description = validateDescription(description);
		this.price = validatePrice(price);
	}

	public void changePrice(BigDecimal newPrice) {
		this.price = validatePrice(newPrice);
	}

	private static String validateDescription(String description) {
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("Description is mandatory");
		}
		return description;
	}

	private static BigDecimal validatePrice(BigDecimal price) {
		if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Price must be greater than 0");
		}
		return price;
	}
}
```

### 7.2 Puerto de salida

```java
package ar.edu.um.progii.catalog.xxx.domain.ports.out;

public interface XxxRepository {

	Xxx save(Xxx xxx);
	Optional<Xxx> findById(Long id);
	List<Xxx> findAll();
	Optional<Xxx> update(Long id, Xxx xxx);
	boolean deleteById(Long id);
}
```

### 7.3 Puerto de entrada

Una interfaz y un método. El método se llama como la interfaz sin `UseCase`, y devuelve el objeto directo, nunca `Optional`.

```java
package ar.edu.um.progii.catalog.xxx.domain.ports.in;

public interface GetXxxByIdUseCase {
	Xxx getXxxById(Long id);
}
```

### 7.4 Excepción

```java
package ar.edu.um.progii.catalog.xxx.application.exception;

public class XxxNotFoundException extends RuntimeException {
	public XxxNotFoundException(Long id) {
		super("Could not find xxx with id: " + id);
	}
}
```

### 7.5 Caso de uso

El puerto `out` se inyecta en un campo `private final` llamado como el tipo en camelCase. La ausencia se convierte en excepción acá.

```java
package ar.edu.um.progii.catalog.xxx.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetXxxByIdUseCaseImpl implements GetXxxByIdUseCase {

	private final XxxRepository xxxRepository;

	@Override
	public Xxx getXxxById(Long id) {
		return xxxRepository.findById(id)
				.orElseThrow(() -> new XxxNotFoundException(id));
	}
}
```

Al borrar: `if (!xxxRepository.deleteById(id)) throw new XxxNotFoundException(id);`.

### 7.6 Persistencia

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.persistence.entity;

@Getter
@Setter
@Entity
@Table(name = "xxx")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxEntity {
	@Id // sin @GeneratedValue si el id viene de afuera (regla 19)
	private Long id;
	private String description;
	private BigDecimal price;
}
```

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.persistence.repository;

public interface JpaXxxRepository extends JpaRepository<XxxEntity, Long> {
}
```

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.persistence.mapper;

@Component
public class XxxMapper {

	public XxxEntity toEntity(Xxx xxx) {
		if (xxx == null) {
			return null;
		}
		return XxxEntity.builder()
				.id(xxx.getId())
				.description(xxx.getDescription())
				.price(xxx.getPrice())
				.build();
	}

	public Xxx toDomainModel(XxxEntity entity) {
		if (entity == null) {
			return null;
		}
		return Xxx.builder()
				.id(entity.getId())
				.description(entity.getDescription())
				.price(entity.getPrice())
				.build();
	}
}
```

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.persistence.adapter;

@Component
@RequiredArgsConstructor
public class JpaXxxRepositoryAdapter implements XxxRepository {

	private final JpaXxxRepository jpaXxxRepository;
	private final XxxMapper xxxMapper;

	@Override
	@Transactional
	public Xxx save(Xxx xxx) {
		XxxEntity saved = jpaXxxRepository.save(xxxMapper.toEntity(xxx));
		return xxxMapper.toDomainModel(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Xxx> findById(Long id) {
		return jpaXxxRepository.findById(id).map(xxxMapper::toDomainModel);
	}

	@Override
	@Transactional
	public Optional<Xxx> update(Long id, Xxx xxx) {
		return jpaXxxRepository.findById(id).map(entity -> {
			entity.setDescription(xxx.getDescription());
			entity.setPrice(xxx.getPrice());
			return xxxMapper.toDomainModel(jpaXxxRepository.save(entity));
		});
	}

	@Override
	@Transactional
	public boolean deleteById(Long id) {
		if (!jpaXxxRepository.existsById(id)) {
			return false;
		}
		jpaXxxRepository.deleteById(id);
		return true;
	}

	// findAll: findAll().stream().map(xxxMapper::toDomainModel).toList(), con readOnly
}
```

### 7.7 Web

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.web.dto;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxRequest {

	@NotBlank(message = "Description is mandatory")
	private String description;

	@NotNull(message = "Price is mandatory")
	@DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
	private BigDecimal price;
}
// XxxResponse: las mismas anotaciones de Lombok, con id y sin validaciones
```

`XxxDtoMapper` sigue el mismo patrón que `XxxMapper`: `toDomain(XxxRequest)` no copia el id y `toResponse(Xxx)` sí.

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.web.controller;

@RestController
@RequestMapping("/xxxs")
@RequiredArgsConstructor
public class XxxController {

	private final GetXxxByIdUseCase getXxxByIdUseCase;
	private final XxxDtoMapper xxxDtoMapper;

	@GetMapping("/{id}")
	public ResponseEntity<XxxResponse> getXxxById(@PathVariable Long id) {
		return ResponseEntity.ok(xxxDtoMapper.toResponse(getXxxByIdUseCase.getXxxById(id)));
	}

	// POST: @Valid @RequestBody XxxRequest → toDomain → puerto → 201 con toResponse
	// DELETE: puerto → ResponseEntity.noContent().build()
}
```

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.web.exception;

@RestControllerAdvice(assignableTypes = XxxController.class)
public class XxxExceptionHandler {

	@ExceptionHandler(XxxNotFoundException.class)
	public ProblemDetail handleNotFound(XxxNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problem.setProperty("code", "XXX_NOT_FOUND");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail handleInvalid(IllegalArgumentException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setProperty("code", "VALIDATION_ERROR");
		return problem;
	}
}
```

Los `code` de error salen del contrato documentado del servicio, no se inventan.

### 7.8 Adaptador de entrada no HTTP

```java
package ar.edu.um.progii.catalog.xxx.infrastructure.scheduler;

@Component
@RequiredArgsConstructor
public class XxxScheduler {

	private final RefreshXxxUseCase refreshXxxUseCase;

	@Scheduled(fixedDelayString = "${xxx.refresh-interval}")
	public void run() {
		refreshXxxUseCase.refreshXxx();
	}
}
```

El listener de Kafka tiene la misma forma: `@KafkaListener` en el método, traduce el `XxxEvent` y llama al puerto `in`.

### 7.9 Migración

```sql
-- V2__create_xxx.sql
-- Qué crea y por qué, en español.
CREATE TABLE xxx (
    id          BIGINT PRIMARY KEY,           -- id externo: sin secuencia
    description VARCHAR(255)   NOT NULL,
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0)
);

CREATE INDEX idx_xxx_description ON xxx (description);
```

Las claves foráneas se llaman `<tabla>_id` y llevan índice, y los índices se llaman `idx_<tabla>_<columnas>`.

## 8. Tests

Van en `src/test/java` con **el mismo paquete** que la clase probada.

| Qué se prueba | Cómo | Dónde |
| --- | --- | --- |
| Modelo y reglas puras | JUnit puro, sin Spring | `xxx/domain/model/XxxTest` |
| `UseCaseImpl` | JUnit puro con un fake en memoria del puerto `out`, instanciando con `new XxxUseCaseImpl(fake)` | `xxx/application/usecases/<Verbo>XxxUseCaseImplTest` |
| Adaptador de persistencia y migraciones | `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` | `xxx/infrastructure/persistence/adapter/JpaXxxRepositoryAdapterTest` |
| Adaptador REST externo | WireMock, sin red real | `xxx/infrastructure/rest/adapter/...Test` |
| Controlador | `@SpringBootTest` + `MockMvc`, chequeando status y `code` | `xxx/infrastructure/web/controller/XxxControllerTest` |
| Cableado completo | `CatalogServiceApplicationTests.contextLoads` (ya existe) | raíz |

- **SIEMPRE** se reusa `<base>.TestcontainersConfiguration`. No se levanta otro contenedor a mano.
- Los tests no dependen de la cátedra, de `.env` ni de la red.
- En los tests se permite `@Autowired` sobre campos.

### 8.1 Fake de un puerto de salida

```java
class GetXxxByIdUseCaseImplTest {

	static class InMemoryXxxRepository implements XxxRepository {
		private final Map<Long, Xxx> data = new HashMap<>();

		public Xxx save(Xxx xxx) { data.put(xxx.getId(), xxx); return xxx; }
		public Optional<Xxx> findById(Long id) { return Optional.ofNullable(data.get(id)); }
		// el resto de los métodos del puerto, en memoria
	}

	private final InMemoryXxxRepository repository = new InMemoryXxxRepository();

	@Test
	void throwsWhenMissing() {
		assertThrows(XxxNotFoundException.class,
				() -> new GetXxxByIdUseCaseImpl(repository).getXxxById(99L));
	}
}
```

## 9. Checklist de verificación

Se corre con `bash` desde la raíz del repo **después de cada cambio de código**. Todos los `grep` y el `find` tienen que devolver vacío.

```bash
B=src/main/java/ar/edu/um/progii/catalog

# Regla 6: sin capa de service
find $B -type d -name service

# Reglas 1 y 2: el dominio no tiene tecnología ni conoce capas externas
grep -rsnE 'import (org\.springframework|jakarta)\.' $B/*/domain
grep -rsnE '\.(application|infrastructure)\.' $B/*/domain

# Regla 14: el dominio no tiene setters
grep -rsnE '@(Setter|Data|NoArgsConstructor)' $B/*/domain

# Regla 3: la aplicación no conoce la infraestructura y solo usa @Component y @Transactional de Spring
grep -rsn '\.infrastructure\.' $B/*/application
grep -rsn 'import org.springframework' $B/*/application | grep -vE 'stereotype\.Component|transaction\.annotation\.Transactional'

# Regla 4: nadie afuera nombra un Impl
grep -rsn 'UseCaseImpl' $B --include='*.java' | grep -v '/application/usecases/'

# Regla 7: los adaptadores de entrada no saltan a la salida
grep -rsnE 'ports\.out|persistence\.' $B/*/infrastructure/{web,kafka,scheduler} 2>/dev/null

# Regla 16: sin inyección por campo
grep -rsn '@Autowired' $B

# Regla 5: entre módulos, solo domain.model y domain.ports.in (shared se puede usar entero)
for m in $(ls -d $B/*/ 2>/dev/null | xargs -n1 basename); do
  grep -rsnE "catalog\.[a-z]+\.(application|infrastructure|domain\.ports\.out)\." $B/$m \
    | grep -vE "catalog\.($m|shared)\." ; done

# Sección 3: shared no depende de ningún otro módulo
grep -rsnE 'import ar\.edu\.um\.progii\.catalog\.[a-z]+\.' $B/shared | grep -v 'catalog\.shared\.'
```

Para terminar:

- [ ] Todos los comandos de arriba no devuelven nada.
- [ ] `./mvnw test` termina en `BUILD SUCCESS`. `contextLoads` falla si falta un bean, si un puerto tiene dos implementaciones o si hay un ciclo.
- [ ] Cada `UseCaseImpl` nuevo tiene su test unitario con fake.
- [ ] Ninguna migración ya commiteada cambió: `git diff --name-only develop -- src/main/resources/db/migration` solo muestra archivos nuevos.

## Requisitos del proyecto

- **Lombok** tiene que estar en el `pom.xml`, con el `maven-compiler-plugin` y Lombok en `annotationProcessorPaths`. Hoy no está: se agrega con el primer módulo que lo use.
- Fuentes de este documento, en el hub `prog-2-proyecto-final`: `docs/proyecto/guias/crear-modulo-hexagonal.md` (plantillas), `docs/apuntes/hexagonal.md` (teoría) y `docs/proyecto/decisiones.md` (decisiones 14, 18, 19 y 20). Si este documento contradice a alguna de esas fuentes, manda este documento.
