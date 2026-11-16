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
    - Tampoco en el vocabulario: los nombres y comentarios del dominio hablan del negocio, no de dónde vienen los datos. Se escribe `idsMissingFromCurrentCatalog`, no `idsMissingFromHashes`; "un cambio publicado por la cátedra", no "una entrada de Redis". Las claves, los comandos y los formatos de cada tecnología se documentan en su adaptador.
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

13. El modelo valida sus invariantes en el constructor y lanza `IllegalArgumentException`. **Toda forma de crearlo pasa por ese constructor**, y cuál se expone depende de cómo se usa el modelo:

    | Si el modelo… | Se crea con | Plantilla |
    | --- | --- | --- |
    | Tiene varios campos que llegan de afuera (base, API, Redis) y los arma un mapper | Constructor público; `@Builder` sobre ese constructor cuando se escribe el mapper que lo llama (regla 26) | 7.1 |
    | Es un conjunto cerrado de variantes, cada una con un significado de negocio | Fábricas estáticas con nombre de negocio (`accepted()`, `rejected(reason)`) y constructor `private`, sin `@Builder` | 7.1b |
    | Tiene uno o dos campos que no se pueden confundir de orden | Constructor público, sin `@Builder` | — |

    - **NUNCA** `@Builder` sobre la clase: genera su propio constructor y saltea las validaciones.
    - Las fábricas existen para que una combinación inválida no se pueda escribir. Si el constructor o un builder siguen siendo públicos, ese beneficio se pierde.
    - Si no está claro qué caso aplica, constructor público.
    - Los getters siguen la regla 26: se agregan los que algún código lee.
14. **NUNCA** `@Setter`, `@Data` ni `@NoArgsConstructor` en `domain`. Los cambios de estado se hacen con métodos con nombre de negocio (`changeXxx`, `disable`).
15. Una regla de negocio sin tecnología (cálculos, decisiones) va en `domain/model`, como clase o método, y se prueba con un test unitario puro. El `UseCaseImpl` la usa para orquestar los puertos; no la reescribe.

**Infraestructura**

16. Inyección **siempre** por constructor: campos `private final` y `@RequiredArgsConstructor`. **NUNCA** `@Autowired` sobre un campo en `src/main`.
    - Una propiedad de configuración se inyecta igual: `@Value("${xxx.yyy}")` sobre el campo `private final`. El `lombok.config` de la raíz hace que Lombok copie ese `@Value` al parámetro del constructor que genera, así que tampoco hace falta escribir el constructor a mano.
17. Los mappers se escriben a mano, con una guarda de `null` al principio, y construyen el modelo por la vía que este expone (regla 13): `builder()`, una fábrica o el constructor. **NUNCA** MapStruct ni ModelMapper.
18. Los métodos del adaptador de persistencia llevan `@Transactional` (`readOnly = true` en las lecturas). `update` carga la entidad existente y le cambia solo los campos que corresponden.
    - 18b. Para escrituras masivas con id externo, el adaptador usa SQL con `JdbcTemplate` (`INSERT ... ON CONFLICT (id) DO UPDATE`) en lugar de `save()` de JPA (decisión 18). Se llama `JdbcXxxWriterAdapter`, va en `infrastructure/persistence/adapter/` e implementa un puerto `out` del módulo (`XxxWriter`). El resto de las reglas no cambia.
        - Si los datos y el estado del módulo tienen que quedar juntos o no quedar (por ejemplo, la réplica y la versión aplicada), el mismo método del writer escribe las dos cosas en una sola transacción (decisión 22). Tres puertos con su propio `@Transactional` commitearían cada uno por su lado. Las entidades JPA de esas tablas quedan solo para leer.
19. Si el id lo asigna un sistema externo (por ejemplo, la cátedra), la entidad lleva `@Id` **sin** `@GeneratedValue`. Si lo genera la base, `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
20. Las validaciones de Bean Validation (`@NotBlank`, `@NotNull`…) van **solo** en los DTO de request. El dominio valida igual con sus propias reglas.
21. Credenciales, tokens y URLs **solo** en `application.properties` como `${VARIABLE}`. **NUNCA** hardcodeados.

**Base de datos**

22. El esquema lo crea **solo** Flyway. Nada de `ddl-auto=create` ni `update`.
23. **NUNCA** editar una migración ya commiteada: cada cambio va en un archivo `V<n>__<descripcion_en_snake_case>.sql` nuevo, con `<n>` igual al último más uno.

**Idioma**

24. Código, clases, tablas, columnas y endpoints en inglés. Comentarios y mensajes de commit en español.

**Nombres**

25. Cada nombre (variable, parámetro, campo, método y parámetro de lambda) dice **qué contiene**, sin que haga falta leer el código de alrededor. Un nombre largo y preciso es mejor que uno corto y ambiguo:
    - **NUNCA** nombres genéricos ni de una letra: `data`, `result`, `value`, `item`, `entry`, `obj`, `tmp`, `list`, `map`, `set`, `x`, `e`. Tampoco en lambdas ni en bucles.
    - **NUNCA** un nombre que solo dice de dónde salió el valor: `Optional<Xxx> fromCache` no dice qué hay adentro. Va `xxxFromCache`.
    - Las colecciones van en plural, y los mapas dicen su clave: `xxxs`, `xxxsById`, `yyyIdsByType`.
    - Si conviven dos valores del mismo tipo con roles distintos, el nombre dice el rol: `expectedVersion` y `declaredVersion`, no `version` y `v`.
    - Un parámetro conserva su nombre en toda la cadena de llamadas: si se llama `yyyIdsByType` en el caso de uso, se llama igual en la regla y en los tests.
    - Los comentarios nombran los valores igual que el código. Las letras o abreviaturas de una issue o del anexo (`L`, `C`, `{v}`) no se usan: se escribe `localVersion < oldestVersion`, no `L < O`.

**Lombok**

26. **Una anotación de Lombok se agrega solo cuando hay código que usa lo que genera, y en el mismo cambio que ese código. NUNCA "por si después hace falta".** Vale para todas las piezas: modelo, entidad y DTO. Un getter, un builder o un setter que nadie llama es API pública sin dueño: hay que mantenerla, habilita usos que nadie diseñó (un setter en una entidad que solo se lee) y obliga a quien lee a buscar llamadas que no existen.
    - **Qué cuenta como uso:** código de `src/main`, o un test que lo necesita para probar otro comportamiento. Un test cuyo único fin es ejercitar lo generado (por ejemplo, que `builder()` arma el objeto) no justifica la anotación.
    - **`@Getter`:** sobre la clase si se leen todos los campos; si se leen algunos, sobre cada campo leído.
    - **`@Builder`:** se agrega con el primer código que lo llama, normalmente el mapper (regla 17). Hasta entonces alcanza el constructor.
    - **`@Setter`:** solo fuera de `domain` (regla 14), y solo en la entidad cuyo adaptador la modifica (`update`, regla 18).
    - **`@AllArgsConstructor`:** solo acompañando a `@Builder` en una entidad o un DTO que también tiene `@NoArgsConstructor`, porque el builder lo necesita para construir.
    - **Al quitar el último uso, se quita la anotación** en el mismo cambio.
    - **Excepciones:** lo que exige un framework se pone siempre, aunque ningún código nuestro lo llame:

      | Pieza | Lleva siempre | Por qué |
      | --- | --- | --- |
      | `XxxEntity` | `@NoArgsConstructor` | JPA crea la entidad con un constructor sin argumentos al leerla de la base |
      | `XxxRequest` | `@NoArgsConstructor` y `@Setter` | Jackson crea el objeto vacío y asigna cada campo al leer el body |
      | `XxxResponse` | `@Getter` | Jackson lee los getters para escribir el JSON |

**DTO de sistemas externos**

27. Los DTO que reciben datos de un sistema externo (`XxxRedisDto`, `XxxApiResponse`) son `record` de Java, **NUNCA** una clase con Lombok. Un `record` declara sus campos en una línea; Java genera el constructor y un método de lectura por campo (`xxxDto.name()`, sin `get`). Jackson lo crea por ese constructor, así que no hace falta `@NoArgsConstructor` ni `@Setter`, y queda inmutable: nadie puede cambiar lo que llegó de afuera.
    - Los componentes copian la forma exacta del JSON externo: mismos nombres en `camelCase` y solo los campos que el servicio usa. Jackson ignora los demás (por ejemplo, `createdAt`).
    - El `record` no valida: las invariantes las controla el modelo cuando el mapper lo construye (reglas 13 y 17).
    - Vale solo para lo que **llega** de un sistema externo. Los DTO web propios (`XxxRequest`, `XxxResponse`) siguen la regla 26.

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
- Dos módulos se pueden usar mutuamente, siempre que cada uno use del otro solo `domain.model` y `domain.ports.in`. Lo que no puede haber es un ciclo entre beans: si dos `UseCaseImpl` se inyectan entre sí, Spring no arranca.
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
    │   ├── dto/            XxxRedisDto.java
    │   ├── mapper/         XxxRedisMapper.java
    │   └── adapter/        RedisXxxSourceAdapter.java
    ├── lock/               adaptador de salida en memoria, sin sistema externo
    │   └── adapter/        InMemoryXxxLockAdapter.java
    ├── kafka/              adaptador de entrada desde Kafka
    │   ├── dto/            XxxEvent.java
    │   └── listener/       XxxEventListener.java
    └── scheduler/          adaptador de entrada por temporizador y al arrancar
        └── XxxScheduler.java
```

Solo existen las carpetas de las tecnologías que el módulo usa. Para una tecnología nueva se agrega una carpeta hermana con su nombre, con subcarpetas por rol (`dto`, `mapper`, `config`, `adapter`, `listener`, `task`), y antes se pregunta si conviene sumarla a este documento.

## 5. Tabla de piezas

| Pieza | Carpeta | Nombre | Anotaciones | Puede depender de |
| --- | --- | --- | --- | --- |
| Modelo | `domain/model` | `Xxx` | creación según la regla 13; Lombok según la regla 26 | `java.*`, Lombok, otros modelos |
| Puerto de entrada | `domain/ports/in` | `<Verbo>XxxUseCase`, un método `<verbo>Xxx(...)` | ninguna | modelo |
| Puerto de salida (base) | `domain/ports/out` | `XxxRepository` | ninguna | modelo |
| Puerto de salida (otro) | `domain/ports/out` | `XxxSource` (lee de afuera), `XxxWriter` (escritura masiva), `XxxPublisher` (publica) | ninguna | modelo |
| Caso de uso | `application/usecases` | `<Verbo>XxxUseCaseImpl` | `@Component`, `@RequiredArgsConstructor` | puertos `in` y `out` del módulo, modelo, puertos `in` de otros módulos |
| Excepción | `application/exception` | `XxxNotFoundException`, `<Motivo>Exception` | ninguna | `java.*` |
| Controlador | `infrastructure/web/controller` | `XxxController` | `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor` | puertos `in`, `XxxDtoMapper`, DTO |
| DTO web | `infrastructure/web/dto` | `XxxRequest` (sin id, con validaciones), `XxxResponse` (con id) | Request: `@NoArgsConstructor @Setter`; Response: `@Getter`; el resto según la regla 26 | `jakarta.validation` |
| DTO externo | `infrastructure/<tecnologia>/dto` | `XxxRedisDto`, `XxxApiResponse` | `record`, sin Lombok (regla 27) | `java.*` |
| Mapper web | `infrastructure/web/mapper` | `XxxDtoMapper`: `toDomain`, `toResponse` | `@Component` | modelo, DTO |
| Handler de errores | `infrastructure/web/exception` | `XxxExceptionHandler` | `@RestControllerAdvice(assignableTypes = XxxController.class)` | excepciones del módulo |
| Entidad JPA | `infrastructure/persistence/entity` | `XxxEntity` | `@Entity @Table @NoArgsConstructor`; el resto según la regla 26 | `jakarta.persistence` |
| Repositorio Spring Data | `infrastructure/persistence/repository` | `JpaXxxRepository extends JpaRepository<XxxEntity, Long>` | ninguna | entidad |
| Mapper de persistencia | `infrastructure/persistence/mapper` | `XxxMapper`: `toEntity`, `toDomainModel` | `@Component` | modelo, entidad |
| Adaptador de persistencia | `infrastructure/persistence/adapter` | `JpaXxxRepositoryAdapter implements XxxRepository` | `@Component`, `@RequiredArgsConstructor`, `@Transactional` por método | puerto `out`, repositorio JPA, mapper |
| Adaptador de escritura masiva | `infrastructure/persistence/adapter` | `JdbcXxxWriterAdapter implements XxxWriter` | `@Component`, `@RequiredArgsConstructor`, `@Transactional` por método | puerto `out`, `JdbcTemplate` |
| Adaptador REST externo | `infrastructure/rest/adapter` | `RestXxxSourceAdapter implements XxxSource` | `@Component`, `@RequiredArgsConstructor` | puerto `out`, `RestClient`, DTO y mapper REST |
| Adaptador Redis | `infrastructure/redis/adapter` | `RedisXxxSourceAdapter implements XxxSource` | `@Component`, `@RequiredArgsConstructor` | puerto `out`, cliente de Redis, mapper |
| Adaptador en memoria | `infrastructure/lock/adapter` | `InMemoryXxxLockAdapter implements XxxLock` | `@Component` | puerto `out`, `java.util.concurrent` |
| Listener de Kafka | `infrastructure/kafka/listener` | `XxxEventListener` | `@Component`, `@RequiredArgsConstructor`, `@KafkaListener` en el método | puertos `in`, DTO del evento |
| Tarea programada | `infrastructure/scheduler` | `XxxScheduler` | `@Component`, `@RequiredArgsConstructor`, `@Scheduled` en el método; `@EventListener(ApplicationReadyEvent.class)` en el que corre al arrancar | puertos `in` |
| Migración | `src/main/resources/db/migration` | `V<n>__<descripcion>.sql` | — | — |

## 6. Recetas

Se trabaja siempre **de adentro hacia afuera**: dominio → aplicación → infraestructura → migración → tests. Empezar por la entidad JPA termina en una entidad disfrazada de dominio.

### A. Crear un módulo nuevo

1. Definir: nombre del concepto (inglés, singular), campos y tipos (`BigDecimal` para dinero), invariantes, operaciones (cada una es un puerto `in`), qué necesita del exterior (cada cosa es un puerto `out`) y quién asigna el id.
2. `domain/model/Xxx.java`: plantilla 7.1, o 7.1b si el modelo es un conjunto cerrado de variantes (regla 13).
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
2. `infrastructure/<tecnologia>/dto/` con la forma exacta de lo que devuelve el sistema externo, como `record` (regla 27).
3. `infrastructure/<tecnologia>/mapper/` de DTO externo a modelo de dominio.
4. `infrastructure/<tecnologia>/adapter/<Tecnologia>XxxSourceAdapter implements XxxSource`. Traduce los errores de la tecnología a excepciones del módulo (en `application/exception`) decididas por el `code`.
5. Las URLs y las credenciales salen de propiedades `${VARIABLE}` (regla 21). Si hace falta un bean de cliente, va en `infrastructure/<tecnologia>/config/`.
6. Test de integración del adaptador contra un doble de la tecnología (WireMock para REST, Testcontainers para Redis).

**De entrada (listener de Kafka, `@Scheduled`):**

1. Llaman a un puerto `in` que ya existe, el mismo que usaría un controlador. **NUNCA** contienen lógica de negocio.
2. El listener deserializa a `infrastructure/kafka/dto/XxxEvent`, lo traduce y llama al puerto. Es idempotente, y el offset se confirma después de persistir.
3. La tarea programada solo dispara el puerto; el intervalo sale de una propiedad.
    - Lo que corre al arrancar el servicio va en la misma clase, en un método con `@EventListener(ApplicationReadyEvent.class)`.
    - Atrapa y loguea las excepciones del puerto, sin decidir nada con ellas. Una excepción que sale del arranque corta el inicio del servicio, y una que sale de `@Scheduled` solo ensucia el log.
    - Si dispara solo, una propiedad `xxx.<disparador>-enabled` lo apaga con `@ConditionalOnProperty`, también al listener de Kafka. Los tests la ponen en `false` en `src/test/resources/config/application.properties`, que Spring carga además del principal. Para Kafka no alcanza `spring.kafka.listener.auto-startup=false`: cuando un test reusa un contexto cacheado, Spring lo reinicia y arranca todos los listeners.

### D. Agregar o cambiar un campo persistido

Orden: modelo (campo + validación) → entidad → `XxxMapper` (las dos direcciones) → adaptador (`update` copia el campo nuevo) → migración **nueva** `V<n>__add_<campo>_to_<tabla>.sql` → DTO y `XxxDtoMapper`, si se expone → tests que cubran el campo.

### E. Regla de negocio pura

1. Clase o método en `domain/model`, sin Spring, sin puertos y sin estado oculto: recibe datos y devuelve una decisión o un valor.
2. Test unitario puro, uno por caso de la regla, sin Spring ni base.
3. El `UseCaseImpl` lee los datos por los puertos `out`, llama a la regla y actúa según lo que devuelve.

## 7. Plantillas

Son ilustrativas: los campos `description` y `price` se reemplazan por los del concepto. Indentación con tabs, como el resto del repo.

Muestran un módulo completo, donde el mapper, el adaptador y el controlador usan todo lo que las plantillas generan. Una pieza real lleva solo las anotaciones de Lombok que su código usa (regla 26): una entidad que nadie modifica no lleva `@Setter`, y un modelo que todavía no arma ningún mapper no lleva `@Builder`.

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

### 7.1b Modelo con variantes cerradas

Cuando el modelo solo puede tomar unas pocas formas, cada una con su significado (regla 13), se crea únicamente con fábricas. El constructor es privado y no hay `@Builder`, así que una combinación inválida, como un rechazo sin motivo, no se puede escribir.

```java
package ar.edu.um.progii.catalog.xxx.domain.model;

import lombok.Getter;

@Getter
public class XxxOutcome {

	private final XxxOutcomeType type;
	private final String reason;

	private XxxOutcome(XxxOutcomeType type, String reason) {
		this.type = validateType(type);
		this.reason = validateReason(type, reason);
	}

	public static XxxOutcome accepted() {
		return new XxxOutcome(XxxOutcomeType.ACCEPTED, null);
	}

	public static XxxOutcome rejected(String reason) {
		return new XxxOutcome(XxxOutcomeType.REJECTED, reason);
	}

	private static XxxOutcomeType validateType(XxxOutcomeType type) {
		if (type == null) {
			throw new IllegalArgumentException("Type is mandatory");
		}
		return type;
	}

	// El constructor sigue validando: es la red de seguridad si alguien agrega una fábrica mal armada
	private static String validateReason(XxxOutcomeType type, String reason) {
		boolean isRejection = type == XxxOutcomeType.REJECTED;
		if (isRejection && (reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("A rejection needs a reason");
		}
		if (!isRejection && reason != null) {
			throw new IllegalArgumentException("Only a rejection carries a reason");
		}
		return reason;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "xxx.automatic-triggers-enabled", havingValue = "true")
public class XxxScheduler {

	private final RefreshXxxUseCase refreshXxxUseCase;

	@EventListener(ApplicationReadyEvent.class)
	public void refreshXxxOnStartup() {
		refreshXxx();
	}

	@Scheduled(fixedDelayString = "${xxx.refresh-interval}", initialDelayString = "${xxx.refresh-interval}")
	public void refreshXxxPeriodically() {
		refreshXxx();
	}

	private void refreshXxx() {
		try {
			refreshXxxUseCase.refreshXxx();
		} catch (RuntimeException refreshException) {
			log.warn("Xxx refresh failed: {}", refreshException.getMessage());
		}
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
# Regla 1: el dominio tampoco nombra tecnologías en nombres ni comentarios
grep -rsniE '\b(redis|kafka|hash(es)?|jpa|jdbc|sql|http|endpoint|json)\b' $B/*/domain

# Regla 14: el dominio no tiene setters
grep -rsnE '@(Setter|Data|NoArgsConstructor)' $B/*/domain

# Regla 13: @Builder nunca sobre la clase (las anotaciones de clase van sin sangría)
grep -rsn '^@Builder' $B/*/domain

# Regla 3: la aplicación no conoce la infraestructura y solo usa @Component y @Transactional de Spring
grep -rsn '\.infrastructure\.' $B/*/application
grep -rsn 'import org.springframework' $B/*/application | grep -vE 'stereotype\.Component|transaction\.annotation\.Transactional'

# Regla 4: nadie afuera nombra un Impl
grep -rsn 'UseCaseImpl' $B --include='*.java' | grep -v '/application/usecases/'

# Regla 7: los adaptadores de entrada no saltan a la salida
grep -rsnE 'ports\.out|persistence\.' $B/*/infrastructure/{web,kafka,scheduler} 2>/dev/null

# Regla 16: sin inyección por campo
grep -rsn '@Autowired' $B

# Regla 25: sin nombres genéricos ni de una letra en variables y lambdas (src/main y src/test)
grep -rsnE '\b(data|result|value|item|entry|obj|tmp|list|map|set|[a-z])( ->|\) ->| = | : )' src/main/java src/test/java

# Regla 27: los DTO de sistemas externos son record, sin Lombok
grep -rsnE '^(public )?(final )?class |import lombok' $B/*/infrastructure/{redis,rest}/dto 2>/dev/null

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
- [ ] Cada anotación de Lombok agregada o que quedó en un archivo tocado tiene un uso en el código (regla 26). Se revisa a mano: el IDE muestra las llamadas a un getter o a `builder()`.
- [ ] Ninguna migración ya commiteada cambió: `git diff --name-only develop -- src/main/resources/db/migration` solo muestra archivos nuevos.

## Requisitos del proyecto

- **Lombok** tiene que estar en el `pom.xml`, con el `maven-compiler-plugin` y Lombok en `annotationProcessorPaths`, y excluido del jar en el `spring-boot-maven-plugin`. Ya está configurado.
- Fuentes de este documento, en el hub `prog-2-proyecto-final`: `docs/proyecto/guias/crear-modulo-hexagonal.md` (plantillas), `docs/apuntes/hexagonal.md` (teoría) y `docs/proyecto/decisiones.md` (decisiones 14, 18, 19 y 20). Si este documento contradice a alguna de esas fuentes, manda este documento.
