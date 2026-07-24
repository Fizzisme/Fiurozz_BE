# Product Service

## Architecture guides

- [Domain-Driven Design for Product Service](docs/architecture/01-domain-driven-design.md)
- [Clean Architecture for Product Service](docs/architecture/02-clean-architecture.md)
- [Product Service architecture blueprint](docs/architecture/03-product-service-architecture-blueprint.md)

## API design

- [Phase 1 Catalog Management API](docs/catalog-management-api/README.md)

## PostgreSQL development database

Start PostgreSQL 18 with a persistent Docker volume:

```powershell
docker compose up -d product-db
docker compose ps
```

To start all currently integrated services and shared infrastructure, run this
from the repository root instead:

```powershell
docker compose up -d
```

The local defaults are:

- Host: `localhost`
- Port: `5432`
- Database: `product_service`
- Username: `product_service`
- Password: `product_service`

Override them with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and
`DB_PASSWORD`. The Compose file consumes every variable except `DB_HOST`, while
Spring Boot consumes all five. Do not use the development password in a shared
or production environment.

Run the service or its tests after PostgreSQL is healthy:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test "-Dspring.docker.compose.enabled=false"
```

Stop PostgreSQL without deleting its data:

```powershell
docker compose stop product-db
```

## Liquibase migrations

Spring Boot automatically applies
`src/main/resources/db/changelog/db.changelog-master.yaml` at startup. The
current changelogs create the project schema and its indexes.

Never edit a changeset after it has been applied to a shared database. Add a new
numbered changelog under `db/changelog/changes/` and include it at the end of the
master changelog instead.
