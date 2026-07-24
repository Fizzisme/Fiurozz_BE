# Clean Architecture for Product Service

## Purpose

This guide explains the problems Clean Architecture solves, how its dependency
rule works, and how to apply it pragmatically in this Spring Boot service. The
goal is not to create the maximum number of layers. The goal is to keep business
behavior easy to change and test while HTTP, PostgreSQL, GitHub, messaging, and
object storage remain replaceable implementation details at the boundary.

## The short answer

Clean Architecture is useful when business code becomes difficult to change
because it is mixed with framework and infrastructure code.

Typical symptoms are:

- A controller contains authorization and publication rules.
- A domain test needs Spring Boot, Docker, and PostgreSQL.
- Changing an HTTP response also changes persistence code.
- Spring Data, Jackson, GitHub SDK, or messaging types appear everywhere.
- One new feature requires edits in global `controller`, `service`,
  `repository`, and `entity` folders.
- Circular dependencies make it unclear which feature owns a rule.

Clean Architecture separates **business policy** from **delivery and storage
mechanisms**. Its central rule is:

> Source-code dependencies point inward, toward the business rules.

It improves maintainability, testability, and the ability to change technical
choices. It does **not** by itself improve throughput, reduce network latency,
or make a distributed system reliable. Those require separate operational
design.

## 1. DDD and Clean Architecture solve different problems

They work well together, but they are not synonyms.

| Question | Domain-Driven Design | Clean Architecture |
|---|---|---|
| What should the model mean? | Ubiquitous language and domain model | Uses that model as the inner policy |
| Where does a model apply? | Bounded contexts and subdomains | Architectural/module boundaries |
| What must be consistent? | Aggregates and invariants | Transactional use cases around them |
| How should code depend on other code? | Not its primary concern | Dependencies point inward |
| How are PostgreSQL and GitHub isolated? | Repositories and anti-corruption concepts | Output ports and adapters |
| How is HTTP isolated? | Not its primary concern | Input port and web adapter |

In one sentence: **DDD discovers the right business model and boundaries;
Clean Architecture protects them from technical coupling.**

Neither requires microservices, event sourcing, CQRS, or one interface per
class.

## 2. The dependency rule

A practical version for Product Service is:

```text
adapter/in/web ───────> application ───────> domain
                               ^
                               │ implements an application-owned port
adapter/out/postgres ──────────┘
adapter/out/github ────────────┘
adapter/out/messaging ─────────┘

configuration wires all concrete objects together
```

Runtime control may travel from the application to PostgreSQL, but the source
dependency remains inward:

1. The application declares the persistence behavior it needs.
2. A PostgreSQL adapter implements that interface.
3. Spring injects the implementation at runtime.

The inner code therefore does not import the outer implementation.

### Allowed dependencies

| Area | May depend on | Must not depend on |
|---|---|---|
| `domain` | Java and domain-owned types | Spring, JDBC, Jackson, HTTP, GitHub SDK, messaging |
| `application` | Domain and application-owned ports | Controllers, database rows, external SDK payloads |
| `adapter/in` | Application API and web/framework types | Persistence implementation |
| `adapter/out` | Application ports, domain types, technical libraries | Inbound controllers |
| `configuration` | All areas for wiring | Business decisions |

Simple values crossing a boundary should be owned by the inner side. Do not
pass `ResponseEntity`, `HttpServletRequest`, Spring Data `Page`, a JDBC row,
GitHub SDK objects, or broker records through application and domain APIs.

## 3. The four practical areas

### 3.1 Domain

The domain contains business concepts and rules:

- Entities and aggregate roots such as `Project` and `ProjectVersion`
- Value objects such as `ProjectSlug`, `VersionName`, and `CommitSha`
- Domain services for rules that do not belong naturally to one entity
- Domain events such as `ProjectVersionPublished`
- Domain exceptions or explicit rule-violation results

It should be possible to construct and test these classes with plain Java.

```java
public final class ProjectVersion {
    private VersionStatus status;
    private Instant publishedAt;

    public void publish(Instant now) {
        if (status != VersionStatus.READY) {
            throw new VersionNotReadyException();
        }

        status = VersionStatus.PUBLISHED;
        publishedAt = now;
    }
}
```

The domain says *what is valid*. It does not open transactions, call GitHub, or
serialize JSON.

### 3.2 Application

The application layer implements use cases. It coordinates the domain and the
ports required to complete one user or system intention.

Responsibilities include:

- Accepting an application command
- Loading aggregate roots through output ports
- Checking authorization through an intentional policy/identity port
- Invoking domain behavior
- Defining a transaction boundary
- Saving state and recording integration work
- Returning an application-owned result

It should not reimplement rules that belong in the aggregate.

```java
public interface CreateProjectUseCase {
    CreateProjectResult handle(CreateProjectCommand command);
}

public record CreateProjectCommand(
    UUID ownerId,
    String title,
    String slug,
    String description
) {}

public record CreateProjectResult(UUID projectId, String slug) {}
```

An output port expresses exactly what the use case needs:

```java
public interface ProjectRepository {
    boolean existsActiveSlug(OwnerId ownerId, ProjectSlug slug);
    Project save(Project project);
    Optional<Project> findById(ProjectId id);
}
```

Do not mirror every `CrudRepository` method. `findAll`, `deleteAll`, and generic
partial updates may allow callers to bypass domain intent.

### 3.3 Input adapters

Input adapters translate an external protocol into an application use case.
Examples are REST controllers, message consumers, scheduled jobs, and CLI
commands.

A REST controller should normally:

1. Validate transport syntax.
2. Translate authentication claims and the request DTO into a command.
3. Call one input port.
4. Translate the result or exception into an HTTP response.

It should not decide whether a version is publishable or manipulate database
rows directly.

```java
@RestController
final class ProjectController {
    private final CreateProjectUseCase createProject;

    ProjectController(CreateProjectUseCase createProject) {
        this.createProject = createProject;
    }

    @PostMapping("/projects")
    ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        var result = createProject.handle(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProjectResponse.from(result));
    }
}
```

`CreateProjectRequest` and `ProjectResponse` belong to the web adapter. They are
allowed to change when the REST contract changes without changing the domain.

### 3.4 Output adapters

Output adapters translate application intent into technical operations:

- Spring Data JDBC or `JdbcClient` to PostgreSQL
- A GitHub API client
- Object-storage access
- An outbox writer or event publisher
- Calls to another service
- Time, identity, or ID generation when those need deterministic tests

For behavior-rich aggregates, an adapter may map between a domain object and a
Spring Data JDBC record:

```java
@Repository
final class JdbcProjectRepositoryAdapter implements ProjectRepository {
    private final SpringDataProjectRepository records;
    private final ProjectPersistenceMapper mapper;

    @Override
    public Project save(Project project) {
        return mapper.toDomain(records.save(mapper.toRecord(project)));
    }
}
```

Separate domain and persistence models add mapping work. Use that separation
where it protects real behavior. A simple read projection or stable reference
table can use a focused JDBC query without manufacturing a rich domain model.

## 4. Organize by business capability first

Avoid a service-wide structure like this:

```text
controller/
service/
repository/
entity/
```

It groups code by technical role, so one feature is scattered across the whole
repository. Prefer business capability first and architectural role second:

```text
com/philia/productservice/
├── ProductServiceApplication.java
├── catalog/
│   ├── domain/
│   ├── application/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── service/
│   └── adapter/
│       ├── in/web/
│       └── out/postgres/
├── release/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── source/
│   ├── domain/
│   ├── application/
│   └── adapter/
├── interaction/
│   ├── domain/
│   ├── application/
│   └── adapter/
└── configuration/
```

This lets a developer find most code for one capability in one place. The
third guide defines the exact Product Service module boundaries and public
APIs.

## 5. End-to-end request flow

For a synchronous `CreateProject` use case:

```text
POST /projects
  -> ProjectController                         input adapter
  -> CreateProjectUseCase                      input port
  -> CreateProjectService                      application
  -> Project.create(...)                       domain
  -> ProjectRepository                         output port
  -> JdbcProjectRepositoryAdapter              output adapter
  -> Spring Data JDBC / PostgreSQL             framework and database
  <- CreateProjectResult
  <- HTTP 201 ProjectResponse
```

The direction of runtime calls goes in and out. The direction of source imports
still points toward application/domain interfaces.

### Workflows with external I/O

Do not keep a PostgreSQL transaction open while calling GitHub, object storage,
or another service. A long external call consumes a database connection and can
leave locks open.

For version preparation, separate the workflow into phases:

```text
1. Read and authorize the preparation request
2. Resolve branch/tag references through the GitHub output adapter
3. Enter a short database transaction
4. Change domain state, save refs/snapshots, and append outbox records
5. Commit
6. Let an idempotent worker deliver and process the requested work
```

If a `@Transactional` phase is called through a Spring proxy, place that phase
on a separate bean or use an explicit transaction boundary. Self-invocation of
an annotated method is not a reliable transaction design.

## 6. A pragmatic Spring policy

There are two reasonable levels of strictness:

- **Pragmatic:** keep the domain pure; allow `@Service` and `@Transactional` on
  application services.
- **Strict:** keep domain and application as plain Java; configure beans and
  transaction decorators in the outer layer.

Use the pragmatic option initially. Spring annotations on an application
service create modest framework coupling and avoid excessive wiring. Do keep
Spring MVC, validation transport annotations, Jackson, Spring Data JDBC, and
external client types out of the domain.

Other rules:

- Use constructor injection.
- Put transactions around complete database use cases, not every repository
  method.
- Inject `Clock` when domain decisions depend on time.
- Generate IDs inside the application/domain or behind a port when tests need
  deterministic values.
- Do not create an interface for a use-case implementation unless it is a real
  boundary, public module contract, or testing seam.
- Do not add generic `BaseService`, `BaseRepository`, or `BaseMapper` classes
  that erase business language.

## 7. Commands, queries, and mapping

Use different shapes for different responsibilities:

| Shape | Owner | Purpose |
|---|---|---|
| Request/response DTO | Input adapter | Versioned HTTP contract |
| Command/result | Application | Stable use-case input and output |
| Entity/value object | Domain | Rules, identity, and behavior |
| JDBC record | Output adapter | Relational persistence mapping |
| Integration event | Boundary/outbox | Versioned cross-service contract |
| Read projection | Query adapter | Efficient list, feed, detail, or search response |

Do not load a large aggregate merely to render a project list. A query use case
may read a projection directly with SQL and return application-owned read data.
Commands should still pass through domain behavior when they change invariants.
This lightweight command/query separation does not require a separate CQRS
framework or database.

Map errors at the boundary:

```text
ProjectNotFound            -> HTTP 404
ProjectSlugAlreadyExists   -> HTTP 409
VersionNotReady            -> HTTP 409
InvalidProjectSlug         -> HTTP 400
ForbiddenProjectOperation  -> HTTP 403
```

The domain must not throw an HTTP-specific exception or choose a status code.

## 8. Testing strategy

Different tests protect different boundaries:

| Test | What it proves | Infrastructure |
|---|---|---|
| Domain unit test | Invariants and state transitions | Plain JUnit only |
| Use-case unit test | Orchestration and calls to ports | Fakes/mocks, no Spring |
| Web slice test | Validation, mapping, security, HTTP codes | Spring MVC slice |
| PostgreSQL adapter test | SQL, constraints, mapping, locking | Real PostgreSQL with Testcontainers |
| Liquibase migration test | Empty database reaches expected schema | Real PostgreSQL |
| Module/architecture test | No cycles or forbidden imports | Spring Modulith or ArchUnit |
| Full integration test | A few critical wired flows | `@SpringBootTest` plus containers |

Keep most business examples in fast domain and use-case tests. Use a real
PostgreSQL instance for SQL semantics; H2 can hide PostgreSQL-specific behavior.

Example use-case test shape:

```java
@Test
void creates_project_and_saves_it() {
    var projects = new InMemoryProjectRepository();
    var useCase = new CreateProjectService(projects, fixedClock, fixedIds);

    var result = useCase.handle(command);

    assertThat(projects.findById(result.projectId())).isPresent();
}
```

## 9. How to adopt it without a rewrite

1. Inventory business use cases and identify the ones with changing rules.
2. Choose one vertical slice, such as `CreateProject` or
   `PrepareProjectVersion`.
3. Create a pure domain model only for its meaningful invariants.
4. Define the input port, command, result, and only the output ports it needs.
5. Move HTTP mapping into an input adapter.
6. Wrap current Spring Data JDBC or SQL access in an output adapter.
7. Add domain, use-case, adapter, and boundary tests.
8. Repeat feature by feature; leave unrelated working code in place.
9. Prevent new cross-module access while old coupling is removed gradually.
10. Review the boundaries whenever a change repeatedly touches several modules.

A useful definition-of-done rule is:

> No domain class imports a framework or adapter class, and no module reaches
> into another module's internal package.

## 10. Common mistakes

- Creating four layers but keeping all rules in one giant application service.
- Defining an interface for every class without a boundary or alternate role.
- Making ports copies of framework APIs instead of business-oriented needs.
- Returning web DTOs or JDBC records from use-case APIs.
- Putting every utility, exception, and value object in an unrestricted
  `common` package.
- Allowing one module to import another module's persistence adapter.
- Mapping simple data through many identical objects without gaining isolation.
- Assuming “database independent” means PostgreSQL can be replaced at zero
  cost. SQL behavior and data migration still matter.
- Using asynchronous events for every call without defining consistency,
  durability, ordering, retry, and idempotency.
- Adding full domain ceremony to simple category/icon CRUD.

Architecture is successful when a change has a predictable home and a small
blast radius, not when the repository contains the largest number of folders.

## Primary references

- Robert C. Martin, [The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- Alistair Cockburn, [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture)
- Robert C. Martin, [Screaming Architecture](https://blog.cleancoder.com/uncle-bob/2011/09/30/Screaming-Architecture.html)
- Spring Framework, [Dependency injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
- Spring Framework, [Declarative transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- Spring Boot, [Testing Spring Boot applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- Spring Boot, [Testcontainers support](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- Spring Modulith, [Verifying module structure](https://docs.spring.io/spring-modulith/reference/verification.html)
