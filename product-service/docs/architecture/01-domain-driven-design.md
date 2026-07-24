# Domain-Driven Design for Product Service

## Purpose

This guide explains what Domain-Driven Design (DDD) solves, how it solves it,
and how to introduce it incrementally in Product Service. It is not a checklist
for adding suffixes such as `Entity`, `Repository`, or `DomainService`. DDD is a
way to keep software aligned with a changing business domain.

## The short answer

DDD is useful when the difficult part of a system is not HTTP or SQL, but the
meaning and interaction of business rules.

For Product Service, examples include:

- What exactly does it mean to publish a project?
- Can a project be public while its source is private?
- When is a project version ready?
- What happens if one of several required repository snapshots fails?
- Can a version point to a snapshot from another repository or commit?
- Who owns counters, tags, comments, GitHub access, and source retention?

Without an explicit model, these rules tend to become duplicated `if`
statements in controllers, jobs, event consumers, and SQL. DDD gives the team a
shared language, explicit boundaries, and objects that protect business
invariants.

DDD does **not** directly solve throughput, network latency, database scaling,
or deployment automation. It makes the business model easier to understand and
change. Operational scaling still requires database, messaging, caching, and
deployment decisions.

## 1. Problems DDD solves in real projects

### 1.1 Business language drifts between people and code

A product manager says “publish the version,” one developer implements
`status = PUBLISHED`, another waits for source snapshots, and another treats the
project page and source code as one visibility setting.

The result is code that uses the same words with different meanings.

DDD addresses this with a **ubiquitous language**: a deliberately maintained
vocabulary used in conversations, requirements, tests, APIs, events, and code.

For this service, the language should distinguish at least:

| Term | Meaning |
|---|---|
| Project | The public or private portfolio/catalog item. |
| Project Version | A release on the platform, possibly composed of several repositories. |
| Repository Link | The association between a Project and one external source repository. |
| Repository Ref | A repository plus a resolved immutable commit used by a version. |
| Source Snapshot | The immutable source artifact for one repository and commit. |
| Project Visibility | Who can see the project page. |
| Source Visibility | Who can read source content. It is independent of project visibility. |
| Ready | All required snapshot work for a version has completed successfully. |
| Project Published | The Project is intentionally visible in the catalog according to Project lifecycle rules. |
| Version Published | A ready Project Version is intentionally released and has a publication timestamp. |

Project publication and version publication are separate facts. Use
`ProjectPublished` for the catalog lifecycle and `ProjectVersionPublished` for
the release lifecycle; neither event is an alias for the other. One application
workflow may coordinate both transitions through their owning modules.

If the team cannot agree on a term, the code should not hide that disagreement.
Resolve it with examples and rules first.

### 1.2 Business rules are scattered

An unhealthy implementation often looks like this:

```text
REST controller checks ownership
  -> service changes a status string
  -> repository saves rows
  -> Kafka listener increments a counter
  -> scheduled job applies a slightly different readiness rule
```

No single place guarantees correctness. A new endpoint can bypass an old rule.

DDD groups data and behavior around **aggregates**. An aggregate root is the
only supported entry point for changes inside that consistency boundary.

Instead of arbitrary status setters:

```java
version.markPublished(now);
```

The method can reject publishing unless the version is ready and can assign the
publication time atomically.

### 1.3 One model becomes a “god model” for the whole company

The word `User` means different things to authentication, billing, messaging,
and Product Service. Trying to share one universal `UserEntity` couples every
team and release.

DDD uses a **bounded context** to state where a model and its language are valid.
Product Service should store identifiers and intentional public snapshots such
as `ownerId`, `ownerDisplayName`, and `ownerAvatarUrl`; it should not import the
User Service persistence model.

### 1.4 Features create cycles and unsafe database access

As the service grows, it is tempting for any package to call any repository.
Soon comments update projects, projects manipulate source jobs, source workers
edit versions, and every feature depends on every other feature.

Strategic DDD makes ownership explicit. Modules communicate through a small API
or events rather than reaching into one another's internal tables and classes.

## 2. Strategic DDD: decide boundaries before classes

### 2.1 Domain and subdomains

The domain is the business problem the system serves. A subdomain is a coherent
part of that problem.

For the current platform:

| Subdomain | Type | Reason |
|---|---|---|
| Project catalog and publication | Core | Directly shapes the platform experience and product value. |
| Version and source snapshot workflow | Core | Enables published projects with reproducible source code. |
| Interaction and discovery | Supporting | Important to engagement, but not the unique source-ingestion capability. |
| GitHub integration | Supporting | Required integration behavior that supports the core. |
| Authentication, email, observability | Generic/external | Reuse or delegate instead of inventing a Product-specific model. |

“Core,” “supporting,” and “generic” guide investment. They do not mean that each
item must immediately become a microservice.

### 2.2 Bounded contexts

A bounded context is a boundary within which one domain model is consistent.
It may be implemented by one module, several modules, or one deployable service.
A microservice is a deployment boundary; a bounded context is a modeling
boundary. They often align, but they are not synonyms.

The current pragmatic choice is:

```text
Product Service deployable
└── Product context
    ├── catalog module
    ├── release module
    ├── source module
    └── interaction module
```

Keep this as a modular monolith first. If source ingestion later has independent
scale, security, or ownership needs, `source` can become a separate bounded
context and deployable service without rewriting catalog rules.

### 2.3 Context relationships

Important relationships are:

```text
User/Auth Context
  └── supplies user identity and authorization claims

Product Context
  ├── owns projects, versions, repositories, snapshots, likes, comments
  ├── stores external user IDs, not User Service tables
  └── publishes product and source lifecycle events

Search Context / projection
  └── consumes events and builds a rebuildable search model

GitHub
  └── external upstream system protected by an anti-corruption adapter
```

An **anti-corruption layer** translates GitHub concepts and payloads into the
Product model. Do not let GitHub SDK classes, webhook JSON, or permission names
become the domain API of the entire service.

## 3. Tactical DDD building blocks

### Entity

An object defined by identity across time. A project's title may change while
the project remains the same project.

Examples: `Project`, `ProjectVersion`, `RepositorySourceSnapshot`, `Comment`.

### Value object

An immutable value defined by its attributes rather than identity. It validates
itself when created.

Examples:

- `ProjectId`, `VersionId`, `RepositoryId`
- `ProjectSlug`
- `CommitSha`
- `ProjectVisibility`, `SourceVisibility`
- `VersionName`

Value objects remove repeated string validation and make invalid values harder
to represent.

### Aggregate and aggregate root

An aggregate is the smallest group of objects that must remain consistent in
one transaction. The root protects its invariants.

Rules of thumb:

- Keep aggregates small.
- Change one aggregate per transaction when practical.
- Reference other aggregates by ID rather than by a large object graph.
- Use events and application orchestration for cross-aggregate workflows.
- Do not design an aggregate to match a screen or every foreign key.

### Repository

A repository provides collection-like access to aggregate roots. It should use
domain language instead of exposing a persistence framework's full API.

```java
public interface ProjectRepository {
    Optional<Project> findById(ProjectId id);
    boolean existsActiveSlug(OwnerId ownerId, ProjectSlug slug);
    Project save(Project project);
}
```

Do not create a repository merely because a table exists. Join tables and child
entities may be persisted as part of an aggregate or through a focused adapter.

### Domain service

Use a domain service when a business operation does not naturally belong to one
entity or value object. It should still express business policy, not database or
HTTP mechanics.

### Domain event

A domain event records something meaningful that already happened:

- `ProjectPublished`
- `ProjectVersionPreparationRequested`
- `SourceSnapshotReady`
- `ProjectLiked`

Use past-tense names. A command asks for work; an event states a fact.

## 4. Suggested aggregate boundaries for Product Service

These are starting hypotheses. Confirm them through real use cases and
transaction rules rather than treating the table diagram as final proof.

| Aggregate root | Likely owned state | Important invariants |
|---|---|---|
| `Project` | Core metadata, status, visibility, and classification reference | Published projects have `publishedAt`; project and source visibility remain separate. |
| `ProjectVersion` | Version state and repository refs needed for preparation | Unique version name per project; publish only from ready state; required refs determine readiness. |
| `RepositoryLink` | GitHub repository mapping and access state | One primary repository per project; repository identity is stable across rename. |
| `RepositorySourceSnapshot` | Snapshot state machine, object metadata, attempts and errors | Identity is repository + commit; ready snapshots have complete immutable artifact metadata. |
| `GitHubIntegration` | Installation identity, access status, synchronized permission snapshot | No long-lived installation token is persisted. |
| `Comment` or comment thread | Comment content, moderation and reply relationship | A reply belongs to the same project as its parent. Keep thread size in mind before making the full thread one aggregate. |

`Tag`, media, likes, and counters require use-case-specific judgment:

- A project edit may update tags in the same transaction without loading a huge
  `Project` object graph.
- A like is naturally an idempotent `(projectId, userId)` association. It does
  not need a behavior-heavy `Like` aggregate.
- High-volume comments or media should not be loaded whenever the Project
  aggregate is loaded.
- Counters are derived projections, not Project aggregate state. Preserve the
  relationship rows as the source for reconciliation, and enforce counter
  non-negativity in the projection/database update path.

## 5. Real workflow example: prepare a project version

### Business command

```text
PrepareProjectVersion(versionId, selectedRepositoryRefs, actorId)
```

### Business facts and rules

1. The actor may manage the project.
2. Each selected repository belongs to the same project as the version.
3. A branch or tag is resolved to an immutable commit SHA.
4. A snapshot is reused when repository + commit already exists.
5. Missing snapshots are requested exactly once logically.
6. The version becomes ready only when every required snapshot is ready.
7. No GitHub or MinIO call occurs while a PostgreSQL transaction is open.

### Practical flow

```text
Application use case
  -> load version and repository ownership data
  -> call GitHub adapter to resolve refs before the write transaction
  -> transaction:
       version.startPreparation(resolvedRefs)
       save version/ref changes
       create or reuse snapshot records
       append outbox events
  -> commit
  -> outbox publisher delivers snapshot requests asynchronously
```

DDD decides the terms, invariants, aggregate boundaries, and events. Clean
Architecture, covered in the next guide, decides how the GitHub, PostgreSQL,
HTTP, and messaging dependencies stay outside the business model.

## 6. How to introduce DDD incrementally

### Step 1: build a language, not a package tree

Hold a short modeling session with product, backend, and frontend participants.
Start from concrete scenarios:

- Publishing with one ready and one failed repository
- Archiving the current version
- Revoked GitHub access after a snapshot is ready
- A private project with public source, or the reverse
- Retrying a failed snapshot

Write commands, events, policies, and unresolved questions. Maintain a glossary
in the repository.

### Step 2: identify invariants and transaction boundaries

For each command, ask:

- What must be true before it runs?
- What must be true immediately after commit?
- What may become consistent later?
- Which single object should reject an invalid transition?

Immediate rules shape aggregates and database constraints. Eventual rules shape
events, outbox records, retries, and reconciliation jobs.

### Step 3: implement one vertical slice

Do not redesign every table and class at once. Start with a meaningful use case,
for example `CreateProject` or `PrepareProjectVersion`:

1. Define command and result types.
2. Introduce value objects for error-prone values.
3. Move the invariant into the aggregate.
4. Define only the persistence/external ports the use case needs.
5. Wrap the existing SQL and web code in adapters.
6. Add pure domain and use-case tests.

### Step 4: protect module boundaries

Use package visibility, a small public module API, and architecture tests. Spring
Modulith can verify that modules have no dependency cycles and that callers use
only exposed APIs.

### Step 5: evolve from evidence

Split a module into another service only when there is evidence such as:

- Independent scaling characteristics
- Different security or isolation requirements
- Different release cadence or team ownership
- Resource-heavy snapshot ingestion affecting catalog requests
- A stable contract between the modules

## 7. Testing a domain model

Domain tests should not require Spring, PostgreSQL, Docker, or HTTP.

```java
@Test
void cannotPublishVersionBeforeSnapshotsAreReady() {
    var version = ProjectVersion.preparing(/* required refs */);

    assertThatThrownBy(() -> version.publish(Instant.parse("2026-07-23T00:00:00Z")))
        .isInstanceOf(VersionNotReadyException.class);
}
```

Also test:

- Every allowed and forbidden state transition
- Value-object validation
- Idempotent commands
- Counter boundaries
- Events produced by successful operations
- Race-sensitive behavior with integration tests and real PostgreSQL constraints

## 8. Common DDD mistakes

- Renaming database records to “entities” while all behavior remains in a giant
  service class.
- Building one aggregate that contains every related table.
- Creating one microservice per entity or table.
- Treating every package as a bounded context.
- Sharing domain entities through a `common-model` library across services.
- Using domain events as mutable integration payloads without versioning.
- Letting controllers directly set aggregate state.
- Repositories exposing generic CRUD operations that bypass business intent.
- Requiring immediate consistency for every cross-aggregate process.
- Applying heavy DDD ceremony to simple configuration or reference-data CRUD.

## 9. When lightweight modeling is enough

Full tactical DDD may not pay for itself when a feature is stable CRUD with no
meaningful invariants. A simple application service and query adapter can be
clearer. Apply richer modeling where rules, terminology, state transitions, or
coordination are genuinely complex.

For Product Service, version preparation and source snapshots deserve a strong
domain model. An admin endpoint that changes a category icon probably does not.

## Primary references

- Eric Evans, [Domain-Driven Design Reference](https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf)
- Martin Fowler, [Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- Spring Data Relational, [Domain Driven Design and Relational Databases](https://docs.spring.io/spring-data/relational/reference/jdbc/domain-driven-design.html)
- Spring Data Relational, [Referenced entities and aggregate references](https://docs.spring.io/spring-data/relational/reference/jdbc/mapping.html#jdbc.entity-persistence.types)
- Spring Modulith, [Application module fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- Spring Modulith, [Verifying module structure](https://docs.spring.io/spring-modulith/reference/verification.html)
