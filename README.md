<div align="center">

# CRM

**An open-source CRM for Italian small businesses: contacts, quotes and invoices with VAT, tickets, reports, workflows, e-invoicing and a customer portal.**

[Live demo](https://crm.alessio.hackclub.app) · [Screenshots](#screenshots) · [Installation](#installation) · [Tech Stack](#tech-stack)

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F)
![Angular](https://img.shields.io/badge/Angular-22-DD0031)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![License: MIT](https://img.shields.io/badge/license-MIT-blue)

![NorthLeap CRM dashboard](docs/screenshots/dashboard.png)

</div>

## Try it now

| | |
|---|---|
| **URL** | https://crm.alessio.hackclub.app |
| **Email** | `viewer@northleap.it` or `demo@northleap.it`|
| **Password** | `HackClub-Demo-2026!` |

The viewer account is **read-only** (`viewer` role) and comes with realistic fake data: companies, contacts, quotes, invoices, tickets, and campaigns. Outgoing email is disabled in the demo environment. Sample data is preloaded so you can explore without filling out dozens of fields first. To test editing and creation, use the demo account (`owner` role).

You can also register with your own credentials, a link will be sent to your email address, for email verification.

> Note that there's only one workspace (the idea behind is that the CRM has to be self-hosted, so there's no need for more instances in the db, it would make no sense)

## Key features

- **Metadata-driven core** Object types and fields are rows in the database (`ObjectType`, `FieldDef`, stored as jsonb in          `Record`), so a new module require no migrations and no new code.
- **Italian document cycle with e-invoicing** Quotes, orders, delivery notes and invoices, with line items and VAT, plus SDI electronic invoicing.
- **Installable PWA** The CRM can be installed on a phone and works like a native app.
- **MCP Server** AI assistants can read and work with CRM data through the MCP.

## Screenshots

| Quote with VAT lines | Full-text search |
|---|---|
| ![Quote](docs/screenshots/quote.png) | ![Search](docs/screenshots/search.png) |

| Customer accepts online | NPS survey |
|---|---|
| ![Accepted](docs/screenshots/quote-accepted.png) | ![NPS](docs/screenshots/nps.png) |

## Tech stack
- Angular 22
- Java Spring
- PostgreSql
- Docker

## Installation

You need Docker. A small VPS (1 vCPU, 2 GB RAM) is enough for a small team. For HTTPS, put it behind a reverse proxy (Caddy, 
Traefik, nginx) and set `CORS_ALLOWED_ORIGINS` in `docker-compose.yml` to your public URL.

```bash
git clone https://github.com/AlessioFerrari8/crm.git
cd crm
cp backend/.env.example backend/.env
# fill the secrets: openssl rand -base64 32 (a different one per line)
docker compose up -d --build
```

| Service | URL |
|---|---|
| App | http://localhost:4200 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |

The first time you open the app it shows **onboarding**: create your workspace and the first user, who becomes `owner`. The endpoint closes itself after first use.

### Data, backup and updates

Data lives in two Docker volumes: `postgres_data` (database) and `uploads_data` (uploaded files). Back up the database with:

```bash
docker compose exec postgres-db pg_dump -U user crm_db > backup.sql
```

To update: `git pull && docker compose up -d --build`. The schema is managed by Hibernate (`ddl-auto=update`, no Flyway), so **take a backup before every update**.

### Run in development

```bash
docker compose up -d postgres-db
cd backend  && ./gradlew bootRun          # http://localhost:8080
cd frontend && npm install && npm start   # http://localhost:4200
```

## Tests

```bash
cd backend  && ./gradlew test      # needs Docker (Testcontainers)
cd frontend && npx ng build
```

Most tests are plain Mockito. The integration tests start a throwaway `postgres:16-alpine` with Testcontainers.

## Contributing and license

Issues, feedback and pull requests are welcome. Released under the [MIT License](LICENSE).

Hope you like this project :)