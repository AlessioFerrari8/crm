> **Note:** the interface is in Italian for now; English i18n is in progress. Meanwhile, use the built-in translate widget (bottom-left corner of the app) or your browser's translator.

<div align="center">

# CRM

**An open-source CRM for Italian small businesses: contacts, quotes and invoices with VAT, tickets, reports, workflows, e-invoicing and a customer portal.**

[Live demo](https://crm.alessio.hackclub.app) · [Screenshots](#screenshots) · [Quick start](#quick-start) · [Architecture](#architecture)

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F)
![Angular](https://img.shields.io/badge/Angular-22-DD0031)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![License: MIT](https://img.shields.io/badge/license-MIT-blue)

![NorthLeap CRM dashboard](docs/screenshots/dashboard.png)

</div>

## Why another CRM?

Most open-source CRMs are either generic or ignore Italian requirements. This one is built around three ideas:

- **Modules are configuration, not code.** Contacts, deals, tickets or your own custom module all live in one generic jsonb-backed model.
- **Italian invoicing out of the box.** VAT by rate and native FatturaPA / SDI XML, no plugin required.
- **Self-hosted and small.** Spring Boot + Angular + PostgreSQL, started with a single `docker compose up`. No external services required.

## Try it now

| | |
|---|---|
| **URL** | https://crm.alessio.hackclub.app |
| **Email** | `viewer@northleap.it` |
| **Password** | `HackClub-Demo-2026!` |

The demo account is **read-only** (`viewer` role) and comes with realistic fake data: companies, contacts, quotes, invoices, tickets, and campaigns. Outgoing email is disabled in the demo environment. Sample data is preloaded so you can explore without filling out dozens of fields first.

To test editing and creation, use this owner account (please don't store anything real in it):

| | |
|---|---|
| **URL** | https://crm.alessio.hackclub.app |
| **Email** | `demo@northleap.it` |
| **Password** | `HackClub-Demo-2026!` |

## Features

- **No-code custom modules**: create and configure modules (companies, contacts, leads, deals, tickets, projects, assets, todos) and custom fields straight from the UI.
- **Sales and invoicing engine**: quotes, orders, and invoices with per-line/header discounts and multi-rate VAT, calculated by a single central engine.
- **Italian e-invoicing**: native FatturaPA / SDI XML file generation.
- **Custom PDF generation**: editable HTML templates with live previews using actual document data.
- **Online quote sign-off**: clients accept proposals via a secure public link—no account required.
- **Isolated customer portal**: dedicated area with separate login and access tokens for ticket tracking and document access.
- **Email campaigns**: batched sending, open/click tracking, and automated unsubscribe management.
- **Email-to-ticket conversion**: connect IMAP mailboxes to automatically convert incoming emails into threaded support tickets.
- **NPS surveys**: lightweight public forms for customer feedback collection.
- **Workflow automation**: trigger-and-condition rules to update fields, fire emails, or execute webhooks (with SSRF protection).
- **Dashboards and reporting**: multi-tab dashboards, visual charts, a custom report builder, and saved view filters.
- **Database-wide search**: instant cross-module full-text search powered by PostgreSQL `pg_trgm`.
- **Core utilities**: tagging, duplicate detection and merging, time tracking, soft-delete trash with restore, and audit logs.
- **Permissions and security**: module and action access control scoped by `OWN` / `TEAM` / `ALL`, 2FA (TOTP), and API key management.
- **Native MCP server**: built-in Model Context Protocol server that allows AI assistants to read CRM data using scoped API keys.
- **PWA and real-time updates**: installable Progressive Web App with real-time SSE notifications.
- **Integrations**: inbound and outbound webhooks, plus native Cal.com synchronization.

## Screenshots

| Quote with VAT lines | Full-text search |
|---|---|
| ![Quote](docs/screenshots/quote.png) | ![Search](docs/screenshots/search.png) |

| Customer accepts online | NPS survey |
|---|---|
| ![Accepted](docs/screenshots/quote-accepted.png) | ![NPS](docs/screenshots/nps.png) |

## Architecture

There is no `contacts` or `companies` table. A single generic `Record` entity stores its fields in a `jsonb` column, and its `objectType` defines what it is. 

The shape of each module is described by `ObjectType` and `FieldDef` rows (39 field types), so **adding a module needs no migration, only a configuration record**. One controller and one service serve all of them, and permissions are checked per module at runtime.

The only deliberate exceptions to the jsonb model are document lines (`document_line`) and totals: those are real columns because we sum them.

## Quick start

You need Docker and Docker Compose. A small VPS (1 vCPU, 2 GB RAM) is enough for a small team. For HTTPS, put it behind a reverse proxy (Caddy, Traefik, nginx) and set `CORS_ALLOWED_ORIGINS` in `docker-compose.yml` to your public URL.

```bash
git clone https://github.com/AlessioFerrari8/crm.git
cd crm
cp backend/.env.example backend/.env
# fill the secrets: openssl rand -base64 32  (a different one per line)
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

## Configuration

Everything is in `backend/.env` (git-ignored); `backend/.env.example` is the template.
These have no default on purpose, because a secret in a repo is a public secret:

| Variable | Purpose |
|---|---|
| `JWT_ACCESS_SECRET` | signs access tokens (15 min) |
| `JWT_REFRESH_SECRET` | signs refresh tokens (30 days), must differ |
| `JWT_PORTAL_SECRET` | customer portal tokens, must differ |
| `QUOTE_ACCEPT_SECRET` | signs quote acceptance links |
| `MFA_SECRET_KEY` | encrypts secrets at rest (TOTP, IMAP passwords), 32 bytes base64 |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials |


**Optional:** outgoing email (`MAIL_PROVIDER=smtp` or `resend`, plus `SMTP_*`), Cal.com webhook, Swagger (`SWAGGER_ENABLED=false` in production).
Without a mail provider everything works except sending: invitations, campaigns, NPS surveys and workflow emails.

## Tests

```bash
cd backend  && ./gradlew test      # needs Docker (Testcontainers)
cd frontend && npx ng build
```

Most tests are plain Mockito. The integration tests start a throwaway
`postgres:16-alpine` with Testcontainers.

## Security

- **httpOnly cookie auth**: JWT tokens never appear in response bodies.
- **CSRF double-submit cookies**: only signed webhooks and public forms are exempt.
- **RBAC**: per-resource and action permissions scoped by OWN / TEAM / ALL.
- **API key management**: keys are stored hashed and displayed only once upon creation.
- **Rate limiting**: per-IP limits on login, refresh, invitations, and webhooks.
- **Upload hardening**: blocked executable extensions, server-generated filenames, path traversal checks.
- **SSRF guard**: workflow webhook URLs resolve DNS to block private, loopback, and link-local addresses.
- **At-rest encryption**: sensitive credentials encrypted at rest.


## Under the hood

Design choices that are intentional:

- **jsonb** columns are `Map` / `List`, never `JsonNode`. Hibernate 7 uses Jackson 2 while Spring MVC uses Jackson 3; mixing library-specific AST types causes runtime failures.
- **PDF model views are standard classes, not Java `record`s.** Thymeleaf expects standard JavaBean getters (`getX()`); records expose `x()`, causing silent empty renders.
- **Document totals have a single source of truth**, `DocumentTotalService`, evaluated in order: line total -> line discount -> header discount -> shipping -> VAT by rate -> grand total. PDF and e-invoice generators read these precomputed values directly.
- **Seeders require explicit `@Order` annotations** (1 Role … 7 Report). Duplicate order values produce non-deterministic startup behavior. 
- **No Flyway migrations.** Hibernate manages schema (`ddl-auto=update`); new columns on populated tables require `@ColumnDefault`.
- **The backend image builds with `bootJar -x test`** inside Docker, so no local Java is needed; run `./gradlew test` separately.

## Contributing and license

Issues, feedback and pull requests are welcome. Released under the [MIT License](LICENSE).

Hope you like this project :)