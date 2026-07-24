# Product Service Architecture Blueprint

## Status and scope

This document turns the [DDD guide](01-domain-driven-design.md) and the
[Clean Architecture guide](02-clean-architecture.md) into a concrete starting
architecture for Product Service.

It is tailored to the current project baseline:

- Java 21 and Spring Boot 4.0.7
- Declared Spring Data JDBC, Liquibase, REST/gRPC, Redis, Batch, and Zipkin
  dependencies
- PostgreSQL migrations for project, version, repository, snapshot,
  interaction, webhook, and outbox tables
- Very little application code, so boundaries can be established before
  features spread across global technical packages

The schema and dependencies scaffold future GitHub, storage, event-publisher,
worker, and gRPC capabilities. Those adapters, workers, and application
contracts are not implemented yet; this document describes their target shape.

This is an implementation blueprint, not a requirement to generate every class
before delivering the first endpoint.

## 1. Recommended architecture

Start with a **modular monolith**:

```text
One Product Service process
└── One Product Service PostgreSQL database
    ├── catalog module
    ├── source module
    ├── release module
    ├── interaction module
    └── optional productview read module
```

Each module is a business boundary with a small public API and hidden internals.
Inside each module, use Clean Architecture around behavior that has meaningful
business rules.

This choice gives the project:

- Local ACID transactions where they are genuinely required
- One deployable service and one Liquibase migration history
- Feature ownership without network calls between packages
- Enforceable dependency rules and faster tests
- A possible extraction seam if one module later needs independent deployment

Do not turn each module into a Docker service now. A package boundary is cheap;
a network boundary adds partial failure, retries, timeouts, versioned contracts,
distributed tracing, deployment coordination, and eventual consistency.

## 2. Architecture decisions to establish now

| Decision | Initial choice | Why |
|---|---|---|
| Deployment | One Product Service application | The features share a lifecycle and the codebase is still small. |
| Data | One database owned only by Product Service | No other service may query or write its tables directly. |
| Internal structure | Business modules, then layers inside each module | A feature has one obvious home. |
| Business model | Rich only where invariants justify it | Avoid both transaction scripts everywhere and unnecessary ceremony. |
| Inter-module calls | Public API for immediate work; events for secondary work | Makes consistency and dependencies explicit. |
| External reliability target | Evolve the existing outbox table into an operational publisher with claims, retries, monitoring, and idempotent consumers | Important messages can survive process failure once the mechanism is implemented. |
| Schema evolution | One append-only Liquibase master | Safe, ordered deployment history. |
| Queries | Focused projections for lists/details | Avoid loading write aggregates for read screens. |
| Future extraction | Based on measured operational/team need | Package growth alone is not evidence for a microservice. |

Record changes to these decisions in short Architecture Decision Records when
the team has evidence that a different trade-off is better.

## 3. Proposed business modules

### Module map

| Module | Owns now | Responsibilities | May depend on |
|---|---|---|---|
| `catalog` | `project_categories`, `project_sub_categories`, `projects`, `tags`, `project_tags`, `project_media` | Project metadata, classification, visibility, lifecycle, and media | Identity/authorization port only |
| `source` | `github_integrations`, `project_repositories`, `repository_source_snapshots`, `github_webhook_deliveries` | Repository attachment, GitHub translation, immutable source snapshots, retries, and webhook deduplication | `catalog::api` |
| `release` | `project_versions`, `project_version_repository_refs` | Version lifecycle, selected repository refs, readiness, and publication workflow | `catalog::api`, `source::api` |
| `interaction` | `project_likes`, `project_comments` | Idempotent likes, comments, moderation behavior, and engagement queries | `catalog::api` |
| `productview` (later) | Read-only projections | Composed project details, feeds, discovery, and search synchronization | Public query APIs/events from all modules |
| `outbox` (technical module) | `outbox_events` | Claims and publishes generic outbox records after modules write them | No business-module internals |

The table list describes ownership, not aggregate boundaries. A module can own
several aggregates and repository adapters.

Keep categories and tags inside `catalog` initially. Promote them to a separate
`taxonomy` module only if they gain an independent language, workflow, or owner.

### Allowed dependency graph

```text
source -------> catalog
release ------> catalog
release ------> source
interaction --> catalog
productview --> catalog, source, release, interaction
```

An arrow points from a caller to the public API it uses. The graph must remain
acyclic.

Examples:

- `release` may ask `source` to resolve or prepare snapshots.
- `release` may tell `catalog` that a valid release has been published.
- `catalog` must not reach into `release` to inspect internal version rows.
- `interaction` may check whether a project accepts interactions, but `catalog`
  must not load likes or comments.
- A composed read model can depend on all module query APIs without becoming a
  write path.

If a new use case appears to require a cycle, first reconsider ownership. A
higher-level workflow, public query, or event usually exposes the missing
concept more clearly than mutual repository access.

## 4. Target package structure

Use direct subpackages of `com.philia.productservice` as business modules. A
module exposes only its `api` package; implementation code remains internal.

```text
src/main/java/com/philia/productservice/
├── ProductServiceApplication.java
├── catalog/
│   ├── package-info.java
│   ├── api/
│   │   ├── package-info.java
│   │   ├── CreateProject.java
│   │   ├── FindProject.java
│   │   ├── ProjectId.java
│   │   └── ProjectPublished.java
│   └── internal/
│       ├── domain/
│       │   ├── Project.java
│       │   └── ProjectSlug.java
│       ├── application/
│       │   ├── CreateProjectHandler.java
│       │   └── port/out/
│       │       ├── ProjectRepository.java
│       │       ├── CurrentActor.java
│       │       └── ProjectEventOutbox.java
│       └── adapter/
│           ├── in/web/
│           └── out/postgres/
├── source/
│   ├── api/
│   └── internal/
│       ├── domain/
│       ├── application/
│       └── adapter/
│           ├── in/event/
│           └── out/{postgres,github,storage}/
├── release/
│   ├── api/
│   └── internal/{domain,application,adapter}/
├── interaction/
│   ├── api/
│   └── internal/{domain,application,adapter}/
├── productview/                  # create only when a composed read model is needed
└── outbox/                       # generic publisher; no imports from module internals
    └── internal/{publisher,postgres}/
```

Do not pre-create empty packages just to match the diagram. Create a package as
the first vertical slice needs it, while preserving the boundary.

Keep an output-port implementation beside its owning module. For example,
`catalog.internal.adapter.out.outbox` implements Catalog's internal event port
and inserts a generic record. The separate `outbox` module only claims and
publishes those records; it never implements or imports another module's
internal interface. Keep module-specific Spring configuration inside the
owning module for the same reason.

`outbox_events` is the one intentional shared technical table: module adapters
may insert immutable event records, while only the outbox publisher updates
delivery state. Treat its row shape as a stable infrastructure contract rather
than allowing arbitrary cross-module table access.

### Public API rules

A module's public API may contain:

- Command/query interfaces used by another module
- Application-owned command and result records
- Stable typed IDs when they must cross a boundary
- Versioned application events

It must not expose:

- Spring Data repositories or JDBC record classes
- Controllers or HTTP DTOs
- An aggregate's mutable internal representation
- GitHub SDK, storage, broker, or framework types
- Generic access that allows another module to edit its tables

The REST API is public to clients but its controller is still internal Java
implementation. Other modules call a use-case API, never a controller.

## 5. Enforce the boundaries

Spring Modulith is a good optional enforcement tool because its module model
matches the direct subpackage structure above. Add a Boot-4-compatible Spring
Modulith release when implementation begins; use its BOM instead of assigning
unrelated versions to each artifact.

Example declarations:

```java
@ApplicationModule(
    allowedDependencies = {"catalog::api", "source::api"}
)
package com.philia.productservice.release;
```

```java
@NamedInterface("api")
package com.philia.productservice.catalog.api;
```

Then enforce the model in a test run by CI:

```java
@Test
void verifiesModuleBoundaries() {
    ApplicationModules.of(ProductServiceApplication.class).verify();
}
```

This verification can detect module cycles, access to internal packages, and
dependencies not allowed by the module declaration. ArchUnit is also valid if
the team wants custom package/import rules without adopting Modulith runtime
features.

Spring Modulith is not required for DDD or Clean Architecture. The boundary
design remains valid even if enforcement is initially package visibility plus
code review.

## 6. First vertical slice: Create Project

Build one use case from the domain to PostgreSQL and HTTP before adding broad
abstractions.

### Suggested classes

```text
catalog/api/
  CreateProject.java              input port
  CreateProjectCommand.java       application input
  ProjectDetailResult.java        shared create/get application output
  ProjectId.java                  stable ID used across module boundaries

catalog/internal/domain/
  Project.java                    aggregate root and invariants
  ProjectSlug.java                validated value object

catalog/internal/application/
  CreateProjectHandler.java       authorization, orchestration, transaction
  port/out/ProjectRepository.java persistence need expressed in domain terms

catalog/internal/adapter/in/web/
  ProjectController.java
  CreateProjectRequest.java
  ProjectResponse.java
  ProjectExceptionHandler.java

catalog/internal/adapter/out/postgres/
  JdbcProjectRepository.java      output adapter
  ProjectRecord.java              Spring Data JDBC mapping
  ProjectPersistenceMapper.java
```

### Flow

```text
HTTP request
  -> validate request shape
  -> map authenticated actor + request to CreateProjectCommand
  -> CreateProjectHandler opens a short transaction
  -> validate ProjectSlug and construct Project
  -> check the scoped slug through ProjectRepository
  -> save aggregate
  -> append ProjectCreated integration event if another context needs it
  -> commit
  -> map ProjectDetailResult to HTTP 201
```

The unique PostgreSQL constraint remains the race-condition backstop. An
application `exists` check produces a friendly error but cannot replace the
constraint. Map the constraint violation to the same conflict result.

`ProjectId` is deliberately in `catalog.api` because `source`, `release`, and
`interaction` reference it. It remains a framework-free value type and may be
used by the internal domain model. IDs that never cross a module boundary stay
internal.

### What this slice proves

- The package/import direction works.
- Transport, application, domain, and persistence models have clear ownership.
- Transaction and error mapping conventions are settled.
- A Testcontainers test proves Spring Data JDBC and Liquibase work with real
  PostgreSQL.
- Future slices can copy a concrete pattern without copying business rules.

Do not build generic base use cases or mappers from this first example. Wait for
at least three genuinely repeated cases before extracting an abstraction.

## 7. Core workflow: Prepare and publish a version

This workflow tests the architecture more realistically because it crosses
modules and performs slow external I/O.

### Responsibilities

| Concern | Owner |
|---|---|
| Actor may manage the project | `catalog` public policy/query |
| Repository belongs to the project | `source` |
| Branch/tag resolves to an immutable commit | GitHub adapter inside `source` |
| Snapshot state and artifact location | `source` |
| Required refs and version readiness | `release` |
| Project catalog publication state | `catalog` |
| Version readiness and version publication | `release` |
| Coordinating a version release with Project state | `release` use case calling `catalog::api` |
| Durable requests and integration events | Module output port implemented by outbox adapter |

### Safe synchronous preparation flow

```text
1. release receives PrepareProjectVersion(versionId, requestedRefs, actorId)
2. release checks authorization through catalog::api
3. source validates repository ownership and resolves branch/tag refs through GitHub
   (no database transaction is held during the remote call)
4. the release application handler opens and owns a short PostgreSQL transaction
5. release reloads/revalidates the version and applies resolved immutable refs
6. source creates or reuses snapshot requests through its public command API,
   joining the caller's transaction through the same transaction manager
7. state and outbox events commit atomically
8. an idempotent worker downloads/uploads source outside a transaction
9. a short transaction marks each snapshot READY or FAILED
10. release consumes durable snapshot events and evaluates version readiness
11. PublishProjectVersion rejects anything not READY
```

If GitHub resolution must be fully asynchronous, step 3 can instead be an
outbox command handled by a worker. The API then returns an accepted/preparing
state rather than pretending the work completed synchronously.

Atomicity in steps 4-7 depends on `source` joining the existing transaction
(normal `REQUIRED` propagation), not opening `REQUIRES_NEW`, and remaining an
in-process module using the same `DataSource` and transaction manager. If
`source` is extracted into another service, replace this local transaction with
an outbox-driven process and compensating/reconciliation behavior.

### Snapshot worker state discipline

```text
REQUESTED -> DOWNLOADING -> PROCESSING -> READY
              |                |
              └--------------> FAILED -> eligible retry

READY or terminal failure -> DELETING -> DELETED
```

For every worker operation:

- Claim work in a short transaction.
- Commit before network or object-storage I/O.
- Use `(project_repository_id, commit_sha)` as the idempotency identity.
- Finish with another short transaction and optimistic-lock check.
- Treat repeated messages and retries as normal.
- Store enough failure information to diagnose and retry safely.

The current snapshot table has timestamps and an attempt counter but no worker
lease. Before running multiple workers, add an append-only migration for fields
such as `lease_owner` and `lease_until`. A worker renews its finite lease while
active; a recovery job returns expired `DOWNLOADING`/`PROCESSING` work to a
retryable state after checking the attempt policy. Without that recovery rule,
a crash after claiming work can strand a snapshot indefinitely.

Never promise exactly-once delivery. Use at-least-once delivery with idempotent
handlers and database uniqueness/locking.

## 8. Aggregate and persistence plan

These are initial aggregate hypotheses. Validate them against real commands and
invariants.

| Root | Keep inside or near it | Keep outside it |
|---|---|---|
| `Project` | Metadata, lifecycle, visibility, classification references | Versions, repositories, snapshots, likes, comments |
| `ProjectVersion` | Version state and its bounded required-ref set | Full repository and snapshot aggregates |
| `RepositoryLink` | External identity, primary flag, access snapshot | Project aggregate and snapshot history |
| `RepositorySourceSnapshot` | Commit identity, status, attempts, immutable artifact metadata | Repository object graph and versions |
| `GitHubIntegration` | Installation identity and access state | Persisted short-lived access token |
| `Comment` | Content, author ID, moderation state, parent ID | An unbounded project comment tree |
| Like association | `(projectId, userId)` uniqueness | Behavior-heavy aggregate unless rules later justify it |

### Spring Data JDBC rules

Spring Data JDBC treats objects reachable from a root as part of that
aggregate. Saving an aggregate can delete and recreate referenced child rows.
Therefore:

- Use one repository per aggregate root, not one per table by habit.
- Keep aggregate object graphs small and bounded.
- Reference other aggregates with typed IDs or `AggregateReference`.
- Do not map versions, repositories, snapshots, likes, and comments as children
  of a single `Project` object.
- Use a custom repository/JDBC adapter for complex joins, composite keys, bulk
  operations, or updates that should not recreate collections.
- Map the existing `row_version` columns with `@Version` where aggregates use
  optimistic locking.

Domain validation and database constraints are complementary. PostgreSQL
unique, foreign-key, check, and partial-index constraints remain the final
integrity boundary under concurrency.

### Counter ownership

The existing `projects.view_count`, `like_count`, and `comment_count` are
denormalized projections, not core Project invariants.

During transition:

- Designate exactly one writer for each counter.
- Use atomic SQL updates, not load-modify-save.
- Keep likes/comments as the reconcilable source of truth.
- Do not make `Project` load interaction rows.

Long term, let `interaction` own engagement statistics and let `productview`
compose them into project responses. This removes cross-module writes to the
catalog table.

## 9. Commands and read models

Write use cases protect domain behavior. Read use cases optimize returned data.

### Command side

- Load only the aggregate state needed to enforce invariants.
- Mutate through domain methods, not public setters.
- Use optimistic locking and database constraints.
- Record required events in the same transaction as the state change.

### Query side

- Use focused SQL projections for feeds, project details, counts, and search.
- Return application-owned read records, then map them to REST/gRPC responses.
- Avoid reconstructing an aggregate just to display data.
- Paginate every potentially unbounded collection.
- Make cross-module SQL reads explicit and read-only; they are a deliberate
  schema dependency owned by `productview`, never a shortcut for writes.

This is a small, practical form of command/query separation. It does not require
event sourcing, a second database, or a CQRS framework.

## 10. Transactions and inter-module communication

### Use a direct public API when

- The caller needs an immediate answer.
- Failure must stop the current local use case.
- The dependency direction stays acyclic.
- Work completes quickly inside the Product database.

### Use an event when

- Secondary work may be eventually consistent.
- One publisher should not know every consumer.
- Retrying independently is important.
- The boundary may later become a service boundary.

### Event categories

| Type | Meaning | Durability |
|---|---|---|
| Domain event | Fact produced by an aggregate | Internal until translated |
| Application/module event | Fact another module may consume | Durable if losing it would break behavior |
| Integration event | Versioned contract for another service | Transactional outbox and idempotent consumer |

The current repository contains an `outbox_events` table, but no publisher,
claim/lease protocol, contract-version field, or operational monitoring. It is
schema scaffolding, not yet a reliable delivery mechanism. Add the required
migrations and implementation before relying on it.

The target operational implementation needs:

- Atomic insert alongside the business transaction
- Claim/lease semantics for multiple publishers
- Retry count, backoff, and failure diagnostics
- Stable event ID and contract version
- Idempotent consumers
- Monitoring for old pending/failed rows

Plain `@Async` or an in-memory listener is not durable across a process crash.
Spring Modulith's JDBC event publication registry is an alternative, not an
automatic wrapper around the existing custom outbox. Do not publish the same
event through both mechanisms. If the team adopts the registry later, migrate
deliberately and create its version-specific tables through Liquibase.

## 11. Liquibase strategy

Keep one Product Service changelog because there is one service-owned database.
The existing `001` and `002` changesets are history: do not edit, move, rename,
or reorder them after they have run in any persistent environment.

Future changes can be grouped by owning capability while remaining explicitly
ordered by the master changelog:

```text
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
    ├── 001-create-project-schema.sql
    ├── 002-create-project-indexes.sql
    ├── catalog/
    │   └── 003-add-project-publication-policy.sql
    ├── interaction/
    │   └── 004-create-interaction-statistics.sql
    ├── source/
    │   └── 005-add-snapshot-worker-lease.sql
    └── release/
        └── 006-add-version-failure-reason.sql
```

Rules:

1. Append explicit `include` entries to `db.changelog-master.yaml`.
2. Give one changeset one focused schema purpose.
3. Never modify a changeset already applied to a shared database.
4. Add rollback SQL only when rollback is actually data-safe.
5. Use expand/contract for zero-downtime changes:
   add compatible structure, deploy compatible code, backfill, enforce, then
   remove old structure in a later release.
6. Test both an empty database migration and an upgrade from the latest released
   schema.
7. Use PostgreSQL Testcontainers rather than H2; the schema uses PostgreSQL
   features such as JSONB, partial indexes, and database-specific constraints.
8. Run Liquibase validation in CI before deployment.

Modules own tables by convention and tests. They do not each need a separate
database, schema, Liquibase process, or Compose file inside this deployable.

## 12. Testing architecture

### Required test levels

| Level | Examples | Tooling |
|---|---|---|
| Domain | Version cannot publish before ready; invalid slug rejected | Plain JUnit |
| Application | Correct port calls, authorization, idempotency, events | Plain JUnit with fakes/mocks |
| Web/gRPC adapter | Validation, authentication mapping, status/error contract | MVC/gRPC slice tests |
| JDBC adapter | Mapping, constraints, custom SQL, optimistic locking | `@DataJdbcTest` + PostgreSQL Testcontainers |
| Migration | Clean install and previous-version upgrade | Liquibase + PostgreSQL container |
| Module boundary | No cycles or internal-package access | Spring Modulith or ArchUnit |
| Module scenario | `SnapshotReady` eventually makes a version ready | Modulith scenario or focused integration test |
| Full application | A few critical wired user journeys | `@SpringBootTest` + containers |

Keep full-context tests few. Most business mistakes should be caught by fast
domain and application tests; infrastructure behavior should be proven against
the real infrastructure it depends on.

### CI gates

At minimum, CI should fail on:

- Compilation or unit-test failure
- Module cycle or forbidden package import
- Liquibase validation failure
- Failure to migrate a clean PostgreSQL database
- Failure to migrate from the last released schema
- JDBC integration-test failure
- Public API/consumer contract incompatibility when contracts exist

## 13. Security and external systems

- Treat the authenticated principal as adapter input. Convert it to an
  application-owned `ActorId`/capability view.
- Do not trust an `ownerId` supplied by a request body.
- Store User Service IDs, not cross-database foreign keys or copied User
  entities.
- Translate GitHub webhooks and SDK objects in the `source` adapter.
- Deduplicate webhooks with their external delivery identity.
- Do not persist short-lived GitHub installation tokens.
- Keep secrets in environment/secret management, never domain objects or
  migrations.
- Apply timeouts, retry classification, rate-limit handling, and observability
  in external adapters.
- Make every externally retried command idempotent or reject duplicates using
  an idempotency key.

## 14. Safe implementation order

### Phase 0: agree on language and rules

- Review the DDD glossary with product/backend/frontend participants.
- Write concrete examples for project publication, version readiness, snapshot
  failure, source visibility, likes, and comments.
- Confirm aggregate and table ownership hypotheses.

### Phase 1: create enforceable module seams

- Add `catalog`, `source`, `release`, and `interaction` module roots.
- Add only the public API types needed by the first feature.
- Add a module verification test with Spring Modulith or ArchUnit.
- Document allowed dependencies.

### Phase 2: deliver one complete catalog slice

- Implement `CreateProject` domain behavior, application handler, JDBC adapter,
  endpoint, and all test levels.
- Establish conventions for commands, results, errors, transactions, IDs, and
  mapping.

### Phase 3: build source integration safely

- Implement repository attachment and GitHub anti-corruption adapters.
- Implement durable, idempotent snapshot work with short transactions.
- Test failure and retry paths, not only success.

### Phase 4: build release workflow

- Implement version state transitions and required refs.
- Consume durable snapshot results.
- Publish only ready versions and protect races with optimistic locking and
  constraints.

### Phase 5: add interactions and read projections

- Implement likes/comments without making them Project children.
- Add a composed `productview` only when an API query actually needs it.
- Move counters toward a single projection owner.

### Phase 6: harden operations

- Monitor outbox backlog, snapshot age/failure rate, GitHub rate limits,
  transaction duration, pool saturation, and optimistic-lock conflicts.
- Load-test query projections and high-volume interaction writes.
- Generate module documentation and revisit dependency rules in review.

## 15. Adding a future feature without losing scalability

For every new feature, use this sequence:

1. Name the business command and result in the ubiquitous language.
2. Decide which existing module owns the rule; create a new module only if the
   language, data, and lifecycle are genuinely cohesive and independent.
3. Write examples and identify immediate versus eventual invariants.
4. Put state-changing rules in an aggregate/value object when they are real
   domain behavior.
5. Define only the inbound and outbound ports the use case needs.
6. Implement one vertical slice through adapter, application, domain, and data.
7. Add tests at the cheapest boundary that proves each risk.
8. Add a Liquibase changeset under the owning capability; do not rewrite
   history.
9. Check that module dependencies remain acyclic.
10. Add metrics for a new asynchronous or external workflow.

Example: project moderation probably belongs in `catalog` if it changes the
Project lifecycle. A reusable moderation case system with appeals, queues, and
its own team may justify a `moderation` module later. The number of classes is
not the deciding factor.

## 16. When to extract a module into a service

Extract only when several signals are concrete:

- It needs independent scaling or resource isolation.
- It has different uptime or failure-containment requirements.
- A separate team owns it and releases independently.
- Its data and public contract are stable enough to own separately.
- It has a different security boundary.
- Its workload harms the rest of Product Service despite local isolation and
  tuning.

Likely candidates, if evidence appears:

1. `source`, because downloads, compression, and object storage are long-running
   and resource-heavy.
2. `interaction`, if its write volume becomes much larger than catalog traffic.

Before extraction, replace synchronous assumptions with a versioned contract,
define data ownership, make consumers idempotent, and plan migration. After
extraction, the new service should get its own database, Dockerfile, Compose
service entry, credentials, migrations, health checks, and observability. It
should not share Product Service tables.

## 17. Definition of done for architecture-sensitive changes

- Business terminology matches the glossary.
- The change has one clear owning module.
- No code imports another module's `internal` package.
- The domain does not import Spring, JDBC, Jackson, HTTP, GitHub, or broker
  classes.
- Transaction boundaries are at application-use-case level and contain no slow
  remote I/O.
- Cross-aggregate and cross-module consistency is explicitly immediate or
  eventual.
- Async handlers are idempotent and important events are durable.
- Database constraints protect race-sensitive integrity.
- New migrations are append-only and tested on PostgreSQL.
- Domain, application, adapter, and architecture risks have proportionate tests.
- Logs/metrics do not expose credentials or tokens.
- A new abstraction removes demonstrated repetition instead of predicted
  repetition.

## 18. Things this design deliberately avoids

- One microservice per table or aggregate
- One Compose file per internal module
- Global `controller`, `service`, `repository`, and `entity` packages
- A mega `Project` aggregate containing the relational graph
- Cross-module repository and table writes as a normal API
- Framework DTOs used as domain or module contracts
- Remote calls inside database transactions
- Plain asynchronous listeners for work that must not be lost
- A custom outbox and Modulith event registry publishing the same event
- H2 as the only proof that PostgreSQL mappings and migrations work
- Rewriting deployed Liquibase files
- A large `common`, `shared`, or `utils` package containing business concepts
- Premature generic base classes and interfaces

## Primary references

- Eric Evans, [Domain-Driven Design Reference](https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf)
- Robert C. Martin, [The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- Spring Data Relational, [Domain Driven Design and relational databases](https://docs.spring.io/spring-data/relational/reference/jdbc/domain-driven-design.html)
- Spring Data Relational, [AggregateReference](https://docs.spring.io/spring-data/relational/docs/current/api/org/springframework/data/jdbc/core/mapping/AggregateReference.html)
- Spring Data Relational, [Optimistic locking](https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html#jdbc.entity-persistence.optimistic-locking)
- Spring Modulith, [Application module fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- Spring Modulith, [Module verification](https://docs.spring.io/spring-modulith/reference/verification.html)
- Spring Modulith, [Application events and event registry](https://docs.spring.io/spring-modulith/reference/events.html)
- Spring Modulith, [Module integration testing](https://docs.spring.io/spring-modulith/reference/testing.html)
- Spring Framework, [Declarative transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- Spring Boot, [Testing Spring Boot applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- Spring Boot, [Testcontainers support](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- Liquibase, [Changesets](https://docs.liquibase.com/community/user-guide-5-0/what-is-a-changeset)
- Liquibase, [`include` changelog entries](https://docs.liquibase.com/secure/reference-guide-5-0/changelog-attributes/include)
- PostgreSQL, [`SELECT` locking clause](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE)
