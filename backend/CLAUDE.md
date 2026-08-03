# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Spring Boot 4.1 / Java 21 backend for NorthLeap CRM. It is an **in-progress migration** of an
existing NestJS + Prisma backend (`crm_geck`, not in this repo) to Spring Boot. The migration
plan and module-by-module design notes live in `../md/` (Italian):

- `../md/00-OVERVIEW.md` — overall plan, stack mapping NestJS→Spring, phase ordering
- `../md/01-AUTH.md` — auth/session/invite design (Phase 1, implemented)
- `../md/02-RBAC.md` — role/permission model (Phase 2, implemented)
- `../md/03-MOTORE-DINAMICO.md` — the metadata-driven record engine (Phase 3, implemented)
- `../md/04-RESTO-MODULI.md` — pages/charts/analytics/webhooks/apikey/files/notifications/audit
  (Phase 4 + Phase 6, both implemented)
- `../md/05-WORKFLOW-ENGINE.md` — workflow engine (Phase 5, implemented)

All 6 phases in `00-OVERVIEW.md` are now implemented, plus two modules present in the original
reference repo but not itemized in any phase doc — `relations` (RecordLink CRUD) and the
`users` invite/accept/manage endpoints (`01-AUTH.md` only specced the `Invite` entity, not its
endpoints) — added to reach full functional parity with the original. See the dedicated sections
below for each.

**Read the relevant `md/` doc before implementing a new module** — each one documents specific
decisions (and reasons) carried over from the original NestJS implementation that aren't
obvious from a clean-room Spring design.

The CRM is **metadata-driven**: instead of fixed tables like `contacts`/`companies`, there is a
generic `Record` entity whose `objectTypeId` says what kind of thing it is, and whose actual
field values live in a JSON `data` column (`@JdbcTypeCode(SqlTypes.JSON)` → `Map<String, Object>`).
Field shape/validation per object type is described by `ObjectType`/`FieldDef` rows, not by SQL
schema. This engine (Phase 3) is the architectural core that pages, charts, analytics, and
workflows all build on — most future work in this repo will eventually touch it.

A companion Angular frontend lives in `../frontend`.

## Build, run, test

From the `backend/` directory, using the Gradle wrapper:

```
./gradlew bootRun                  # run the app (Windows: gradlew.bat)
./gradlew build                    # full build
./gradlew test                     # run all tests
./gradlew test --tests "it.northleap.backend.BackendApplicationTests"   # single test class
```

Running the app requires a PostgreSQL instance. `application.properties` points at
`localhost:5433`, which is where `docker-compose.yml` (repo root) publishes it (`crm-postgres`,
db `crm_db`, user/pass `user`/`user`) — note the compose credentials differ from the
`postgres`/`postgres` default in `application.properties`, so `DB_USERNAME`/`DB_PASSWORD` have to
be set accordingly when running `bootRun` against the compose database.

Running the **tests** requires Docker, not a database: the one test that loads the full Spring
context (`BackendApplicationTests`) extends `AbstractIntegrationTest`, which starts a throwaway
`postgres:16-alpine` via Testcontainers and wires it in with `@ServiceConnection`. The container
is `static`, so one starts per JVM and is reused across subclasses via the Spring test-context
cache; the profile-specific `src/test/resources/application-test.properties` supplies the JWT
secrets (which have no default in `application.properties`) and sets `ddl-auto=create`. Extend
`AbstractIntegrationTest` for any future `@SpringBootTest`, most notably the MockMvc integration
tests the hardening round showed this project needs. The other 205 tests are pure Mockito and
need nothing.

Testcontainers versions are **not** managed by the Spring Boot 4 BOM — `build.gradle` imports
`org.testcontainers:testcontainers-bom` explicitly. Testcontainers 2.x also renamed the module
coordinates (`testcontainers-junit-jupiter`/`testcontainers-postgresql`, not `junit-jupiter`/
`postgresql`).

Required env vars (see `.env`, gitignored): `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` (base64,
used as HMAC-SHA256 keys), `DB_USERNAME`, `DB_PASSWORD`.

Optional env vars for the workflow engine's `send_email` action (Phase 5): `RESEND_API_KEY`,
`RESEND_FROM_EMAIL` — both default to empty, in which case the action just skips (see the
Workflow engine section below for why there's no hardcoded default). `WORKFLOW_EXECUTOR_*`
(core/max pool size, queue capacity) tune the dedicated `@Async` thread pool, sensible defaults
provided. `UPLOAD_DIR` (Phase 6, Files module) — local disk directory for uploaded files,
defaults to `./uploads`, created automatically if missing.

Note: the design plan (`00-OVERVIEW.md`) calls for MySQL (for native `JSON_EXTRACT` path
queries), but the current implementation runs on PostgreSQL — this is a live deviation from the
written plan, not yet reconciled in the docs.

**Jackson note**: Spring Boot 4.1 defaults to Jackson 3 (`tools.jackson.*`) for HTTP
(de)serialization, but Hibernate ORM 7.2 (the version in use) only knows how to drive
`@JdbcTypeCode(SqlTypes.JSON)` columns through classic Jackson 2
(`com.fasterxml.jackson.databind`, pulled in transitively by `jjwt-jackson`) — its
`JacksonIntegration` class has no Jackson-3 awareness. Mixing the two breaks at runtime
(`Cannot construct instance of JsonNode`) if a JSON-column entity field is typed as a
library-specific tree type. The fix used throughout this codebase: **never type a JSON-column
field as `JsonNode`** (either Jackson major version) — use plain `Object`/`Map<String,Object>`/
`List<Map<String,Object>>` instead. Both Jackson 2 (Hibernate's internal use) and Jackson 3
(Spring MVC's HTTP layer) serialize/deserialize generic Java collections identically, so this
sidesteps the version conflict entirely. Keep this in mind for any future `@JdbcTypeCode(SqlTypes.JSON)` field.

## Architecture / package layout

Standard layered structure under `it.northleap.backend`, one folder per concern (not per
feature): `controllers/`, `services/`, `repositories/`, `entities/`, `dtos/`, `security/`,
`config/`, `exceptions/`. This is a deliberate departure from the original NestJS modules (which
mix controller+service+DTO per feature file) — keep new code in the matching folder rather than
co-locating by feature.

### Auth flow (Phase 1 — implemented)

All endpoints live under `/api/auth` in `AuthController`/`AuthService`:
- `GET /status` (public) — `{onboarded}`, based on whether a `Workspace` row exists and is onboarded.
- `POST /onboarding` (public, self-locking — 400 once a workspace is already onboarded) — creates
  the `Workspace`, the first `User`, assigns the seeded `"owner"` `Role` via `UserRole`, and logs
  them in (returns tokens like login).
- `POST /login` (public) — authenticates via `AuthenticationManager`, issues both tokens.
- `POST /refresh` (public) — validates the refresh JWT, looks up the matching `Session` by
  `JwtService.hashToken(...)`, rejects if missing/revoked/expired, **rotates**: revokes the old
  `Session` and issues a fresh access+refresh pair (mitigates replay).
- `POST /logout` (authenticated) — revokes the `Session` matching the refresh token in the body.
- `GET /me` (authenticated) — current user + role keys (`["owner"]` etc.) resolved via `UserRole`.

All four token-issuing endpoints (login/onboarding/refresh) return the same `AuthResponse`
(`accessToken`, `refreshToken`, `userId`, `email`, `name`).

- `JwtService` issues two independent token types signed with **separate secrets**: short-lived
  access tokens (`generateAccessToken`, subject = email, 15 min) and long-lived refresh tokens
  (`generateRefreshToken`, subject = user id + random `jti`, 30 days). `JwtService.hashToken(...)`
  (SHA-256 hex) is what gets persisted in `Session.refreshHash` — the raw refresh token is never
  stored.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`) reads `Authorization: Bearer`, extracts the
  email via the **access** secret, loads `UserDetails` via `UserDetailsServiceImpl`, and sets the
  `SecurityContext` if valid. Invalid/missing tokens just fall through unauthenticated (Spring
  Security handles the 401/403 downstream) — there is no early-exit error response in the filter
  itself.
- `SecurityConfig` is stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled.
  `GET /api/auth/status`, `POST /api/auth/onboarding`, `POST /api/auth/login`, and
  `POST /api/auth/refresh` are `permitAll()`; everything else (including `/logout`, `/me`)
  requires authentication. Update the matcher list here when adding new public endpoints.
- `UserPrincipal` wraps `User` for Spring Security; it currently hardcodes a single
  `ROLE_USER` Spring-Security authority regardless of the user's actual `Role`s — that authority
  is unrelated to the app-level RBAC roles returned by `/auth/me`. Real permission resolution
  (R/W/X per resource) is Phase 2 and not implemented.
- Auth errors are centralized in `AuthException` (`@RestControllerAdvice`): bad
  credentials/unknown user both return a generic 401 "Invalid credentials" message
  (**deliberately** indistinguishable, per `01-AUTH.md`); invalid/expired/revoked refresh tokens
  similarly return a generic 401 "Invalid refresh token" via `InvalidRefreshTokenException`.

### Role / UserRole (minimal, Phase-1-scoped — not full RBAC)

`Role` (id, `key`, `label`, `isSystem`) and `UserRole` (composite-key join entity, `@EmbeddedId`)
exist to support onboarding (assigns `"owner"`) and `/auth/me` (lists role keys). A
`RoleSeeder` (`config/RoleSeeder.java`, `ApplicationRunner`, `@Order(1)`) inserts the 5 system
role keys (owner/admin/manager/agent/viewer) on first startup if the `Role` table is empty.
`Invite` has a `Role` FK and a repository, but there are no invite create/accept controller
endpoints yet (not in `01-AUTH.md`'s endpoint list — gating "who can invite" now *can* use Phase 2
RBAC, but the endpoints themselves are still unbuilt).

### RBAC (Phase 2 — implemented)

Per `02-RBAC.md`. `Permission` (`entities/Permission.java`) is the R/W/X model: `role` (FK),
`resource` (free-text string — an `ObjectType` key like `"contact"`, or a fixed system resource
like `"page"`/`"chart"`/`"workflow"`/`"user"`/`"apikey"` — deliberately not a FK to `ObjectType`,
since `ObjectType` doesn't exist yet and `Permission` must also cover non-`ObjectType` resources),
`canRead`/`canWrite`/`canExecute` booleans, `scope` (`PermScope` enum `OWN`/`TEAM`/`ALL`). Unique
constraint on `(role_id, resource)`.

- `RbacService.resolve(roleIds, resource, action)` looks up all `Permission` rows for the given
  roles+resource, filters to the ones granting the requested `PermAction` (`READ`/`WRITE`/
  `EXECUTE`), and returns `allowed` + the **widest** matching scope (`ALL` > `TEAM` > `OWN`) when
  a user holds multiple roles with different scopes on the same resource. `TEAM` has no extra
  filtering logic yet (no "team" concept exists in the model) — behaves like `ALL` for now.
- `@RequirePerm(resource = "...", action = PermAction.WRITE)` is a method-level annotation for
  controllers. `RbacInterceptor` (`security/RbacInterceptor.java`, global `HandlerInterceptor`
  registered via `config/WebMvcConfig.java`) reads the annotation in `preHandle`, resolves via
  `RbacService`, and throws `RbacDeniedException` → 403 (`exceptions/RbacExceptionAdvice.java`)
  if not allowed. An interceptor was chosen over Spring AOP per the doc, to stay conceptually
  close to the original NestJS `RbacGuard`. `ObjectsController` (Phase 3) uses
  `@RequirePerm(resource = "page", ...)` on every endpoint — object-type/field management is
  gated by the `"page"` resource in the original, a quirk kept for fidelity. `RecordsController`
  (Phase 3) does **not** use `@RequirePerm` — see the Dynamic engine section below for why.
- `Actor` (`security/Actor.java`, a plain record, not a JPA entity) represents "who is making the
  request" — `id`, `type` (`ActorType.USER`/`APIKEY`), `email`, `roleIds`. It is deliberately
  **separate** from `UserDetails`/`UserPrincipal`: `UserDetails` answers "is this request
  authenticated" for Spring Security, `Actor` answers "what can this request do" for the RBAC
  layer. `JwtAuthenticationFilter` builds the `Actor` for the `USER` case (via
  `UserRoleRepository`) and attaches it as a request attribute (`Actor.REQUEST_ATTRIBUTE`) right
  after setting the `SecurityContext`. Controllers can receive it directly via the
  `@CurrentActor` parameter annotation (`security/CurrentActorArgumentResolver.java`), analogous
  to a NestJS `@CurrentUser()` decorator. The `APIKEY` actor type is modeled in `ActorType` but
  **not resolved** — populating `Actor` from an `X-Api-Key` header needs the `ApiKey` entity,
  which is Phase 6, not built yet.
- `PermissionSeeder` (`config/PermissionSeeder.java`, `ApplicationRunner`, `@Order(3)`, runs
  after `RoleSeeder` and `ObjectTypeSeeder`) seeds default permissions for the 5 system roles
  over `fixed resources (page/chart/workflow/user/apikey) ∪ every current ObjectType key`:
  owner/admin/manager get full R/W/X, agent gets R/W (no X), viewer gets R only — all scope
  `ALL`. Does a per-`(role, resource)` **upsert** (insert-if-missing, not "skip if table
  non-empty") so it stays correct as new `ObjectType`s get added later — this replaced Phase 2's
  original simpler "skip if any row exists" seeder once Phase 3 added a growing resource list.

### Dynamic engine (Phase 3 — implemented)

Per `03-MOTORE-DINAMICO.md`. Faithful port of the original's `ObjectType`/`FieldDef`/`Record`/
`RecordLink` engine, `RecordValidator`, `filter-builder.ts`, `records.service.ts`/
`objects.service.ts`. The original repo (`crm_geck`) wasn't in this workspace; it was cloned
from a URL the user shared for reference (not part of this repo) to get the exact `FieldType`
enum, validator coercion rules, and the 19-entry default `ObjectType` seed list.

- **Entities**: `ObjectType` (key/label/icon/color/`sortOrder`, `@OneToMany` to `FieldDef`),
  `FieldDef` (`type` is the 39-value `FieldType` enum; `defaultValue`/`options`/`config` are
  generic `Object`/`List<Map<String,Object>>`/`Map<String,Object>` — see the Jackson note above
  for why, not `JsonNode`), `Record` (`objectType` FK, `title`/`status` denormalized, `ownerId`
  for RBAC `OWN` scope, `data` as `Map<String,Object>` mapped `jsonb`, `isDeleted` soft-delete),
  `RecordLink` (typed link between two `Record`s, free `relationKey`). `FieldDef.objectType` is
  `@JsonIgnore`d to break the `ObjectType.fields ↔ FieldDef.objectType` serialization cycle
  (Jackson would otherwise recurse until it hits `StreamWriteConstraints`'s nesting-depth limit).
  Postgres-reserved-word dodges, same reasoning as `User`→`users`: `order`→`sortOrder`
  (`sort_order` column), `unique`→`isUnique` (`is_unique` column).
- `Page`/`AuditLog` are **minimal pulled-forward entities** (full versions are Phase 4/6,
  `04-RESTO-MODULI.md`): only what `PageGeneratorService.generate(...)` and
  `AuditService.log(...)` need to write. No `PageController`/audit-log read endpoint yet.
- `RecordValidator` (`services/RecordValidator.java`) — `validate(fields, rawData)` coerces/
  validates each field per its `FieldType` (numeric bounds, string length/pattern, email/url/
  color regex, select-options whitelist, relation single-vs-multiple via `config.multiple`,
  permissive multi-format date parsing normalized to ISO-8601, GEO `{lat,lng}`), throws
  `RecordValidationException` (400) on failure. `deriveTitle`/`deriveStatus` infer `Record.title`/
  `status` when not given explicitly. Covered by `RecordValidatorTest` (one test per `FieldType`
  group, no DB needed).
- **Dynamic filters**: `RecordQueryService` builds queries via `EntityManager.createNativeQuery`
  (a `JpaRepository` can't express arbitrary-depth dynamic AND/OR). Adapted for Postgres: each
  filter/sort field is validated against `SAFE_KEY` (`^[a-zA-Z0-9_]{1,64}$`, anti-injection into
  the JSON path, same regex the original mandates), then for dynamic (non-native) fields resolves
  the `FieldDef.type` to pick a cast — `(data->>'field')::numeric` / `::timestamptz` /
  `::boolean` — since Postgres's `->>` always returns text (the original relies on MySQL
  `JSON_EXTRACT`'s automatic type coercion, which Postgres doesn't do). All values are bound as
  JDBC parameters; only the (whitelisted) field name and chosen cast are ever interpolated into
  SQL text. `dtos/FilterGroup.java`'s `conditions` is `List<Object>` on purpose — nested
  conditions/groups are inspected at runtime for a `"combinator"` key (same structural
  duck-typing the original's `isGroup()` does), not modeled as a Java union type. The actual
  SQL-fragment compilation (`SAFE_KEY`, casts, operator handling, AND/OR group recursion) lives in
  a separate `RecordFilterCompiler` (`compileWhere`/`compileOrderBy`), split out from
  `RecordQueryService` specifically so it's unit-testable without a DB — `RecordQueryService` only
  owns `EntityManager` execution and the native-column (status/q/scope) WHERE assembly now. See
  `RecordFilterCompilerTest` for per-operator/cast/nesting/injection-guard coverage (this logic
  previously had no automated tests despite building raw SQL text).
- **`ObjectsController`/`ObjectsService`** — CRUD on `ObjectType`+`FieldDef`. Note: when a
  `FieldDef` collection on a managed `ObjectType` has already been touched in the current
  transaction, Hibernate's persistence-context identity map returns the *same* Java instance on a
  later `findByKey` without re-querying — `create`/`addField` explicitly append the newly-saved
  `FieldDef` to `obj.getFields()` so the response reflects it (`removeField` doesn't need this:
  it never touches the collection before deleting, so the eventual lazy load is fresh).
- **`RecordsController`/`RecordsService`** — generic CRUD over `{key}` (any `ObjectType`).
  RBAC here is **not** `@RequirePerm`: the resource is the dynamic `{key}` path variable, unknown
  at annotation time, so `RecordsService` calls `RbacService.resolve(actor.roleIds(), key,
  action)` directly (same as the original's private `authorize()` helper) and applies `OWN`
  scope itself (query: `ownerId = actor.id`; update: `RbacDeniedException` if the actor isn't the
  owner — that exception now has a `String message` constructor for this specific case, the
  generic no-arg one stays for plain permission denial). Every write publishes a
  `RecordCreatedEvent`/`RecordUpdatedEvent`/`RecordDeletedEvent` (new `events/` package, plain
  Java records — subscribed by `WorkflowEngine` since Phase 5) and calls `AuditService.log(...)`.
  `PATCH` merges the existing `data` with the patch (not a full overwrite) before re-validating.
  `RecordUpdatedEvent` carries both `before` (pre-update `data`) and `beforeStatus` (pre-update
  `status` column) — the latter added in Phase 5 specifically for the workflow engine's
  `field.changed` trigger detection (see below), since `status` lives in its own column, not
  inside `data`.
- **Seeders**: `ObjectTypeSeeder` (`config/`, `@Order(2)`, after `RoleSeeder`) ports the 19
  default `ObjectType`+`FieldDef` definitions (company/contact/lead/.../reminder) from the
  original's seed script, calling `PageGeneratorService.generate` per type. Deliberately
  **excludes** the original seed script's demo `Workflow` rows (Phase 5) and mock `Record` data
  (demo-only, not in `03-MOTORE-DINAMICO.md`'s step list).
- **Out of scope this phase** (confirmed): `GET /logs` audit read endpoint — Phase 6 per
  `04-RESTO-MODULI.md`. (Page/Chart CRUD and analytics moved from "out of scope" to **implemented**
  in Phase 4, see below.)

### Pages / Charts / Analytics (Phase 4 — implemented)

Per `04-RESTO-MODULI.md`. Faithful port of the original's `pages.module.ts`/`charts.module.ts`/
`analytics.module.ts` (each inlines controller+service+DTO in one file in the original; split
across this repo's `controllers/`/`services/`/`dtos/` per the layout convention). Reuses Phase 3's
`Page` entity and `Record`/`RecordRepository` — `Chart.run()` and `AnalyticsService` do flat
`objectTypeId + isDeleted=false` reads, not the dynamic filter engine, so no new query
infrastructure was needed.

- **Entities**: `ChartType` (enum `BAR/LINE/PIE/FUNNEL/KPI/TABLE`), `Chart` (`page` FK nullable,
  `@JsonIgnore`d for the same `ObjectType.fields ↔ FieldDef.objectType`-style cycle reason as
  Phase 3 — `Page` now has a `charts` collection back-reference; `query` as `Map<String,Object>`,
  **not** `JsonNode`, per the Jackson note above). `Page.charts` (`@OneToMany(mappedBy = "page")`)
  added to the Phase-3 minimal `Page` entity.
- `PageService.remove(key)` throws `BadRequestException` (400) for `isSystem` pages. The original
  (`pages.module.ts`) actually throws `NotFoundException` there for this case — a one-off
  inconsistency in the original, not reproduced here; `BadRequestException` was chosen instead to
  match how `ObjectsService.remove` already handles the analogous `isSystem` case elsewhere in
  this codebase (internal-consistency fix, flagged deliberately rather than silently diverging).
- `ChartQueryDto.aggregate` is a loose `String` (`"count"`/`"sum"`, compared case-insensitively),
  matching the original's loose `'count'|'sum'` union rather than a strict enum. The original's
  `ChartQueryDto` also declares a `filters` field that its `run()` never actually applies (dead
  field in the original) — **not ported**, consistent with not "improving" beyond the original
  mid-port.
- `ChartService.run(id)`: loads the `Chart`, resolves `query.objectKey` to an `ObjectType` (400
  if missing), fetches via `RecordRepository.findByObjectType_IdAndIsDeletedFalse`, buckets by
  `groupBy` (the `status` column if `groupBy == "status"`, else `data.get(groupBy)`), aggregates
  by `count` (default) or `sum` of `data.get(field)`. Bucket-value building uses a real `if/else`
  helper (`buildDataPoint`), not a ternary — a `cond ? doubleValue : longValue` ternary unifies
  both branches to `double` at the expression's own static type per JLS regardless of which
  branch runs, silently turning `count` results into e.g. `2.0` instead of `2`; caught by
  `ChartServiceTest` before it reached a live run.
- `AnalyticsService` (`revenue`/`efficiency`/`pipeline`/`activity`) takes an explicit
  `LocalDate referenceDate` on a package-private overload (public no-arg defaults to
  `LocalDate.now()`), purely so `lastMonths(...)`'s month-bucketing (`YearMonth`/`Instant`/
  `ZoneOffset.UTC`) is unit-testable without mocking the clock. `numField()` replicates the
  original's "skip zero/NaN, take first truthy" field-resolution semantics.
- `ActivityPoint.attivita` carries `@JsonProperty("attività")` to preserve the original API's
  accented JSON key for the frontend.
- `PageController`/`ChartController` use `@RequirePerm(resource = "page"/"chart", ...)` — `"page"`
  already used by `ObjectsController` (Phase 3), `"chart"` already one of the 5 fixed resources
  `PermissionSeeder` seeds (no seeder change needed). `AnalyticsController` has **no**
  `@RequirePerm`, matching the original (any authenticated user can view analytics; covered by
  `SecurityConfig`'s blanket `anyRequest().authenticated()`).
- `PageGeneratorService.generate(objectTypeId)` gained an `isSystem` overload
  (`generate(objectTypeId, isSystem)`). The original splits this across two places: the reusable
  `page-generator.service.ts` (used by `ObjectsService` for user-created object types, never sets
  `isSystem`) and `seed.ts` (duplicates the page-creation logic inline with `isSystem: true` for
  the 19 bootstrap object types). This port uses one method with a boolean parameter instead of
  duplicating the logic — `ObjectTypeSeeder` calls `generate(id, true)`, `ObjectsService` (Phase 3,
  user-created types) calls the single-arg overload which defaults to `false`. Fixes a real bug
  found via live smoke testing: bootstrap-seeded pages (e.g. `opportunity:list`) were not
  protected from deletion before this fix.
- **Out of scope this phase** (confirmed): webhooks, API keys, files, notifications, audit-log
  read endpoint — Phase 6 per `04-RESTO-MODULI.md`.

### Workflow engine (Phase 5 — implemented)

Per `05-WORKFLOW-ENGINE.md`. Faithful port of the original's `condition.ts`/`graph-runner.ts`/
`workflow.engine.ts`/`workflows.module.ts`, including the 12 default automations from
`prisma/seed.ts`. The original runs on BullMQ/Redis (external queue + separate worker process);
this port replaces that with Spring `@Async` + a dedicated `TaskExecutor`
(`config/AsyncConfig.java`, bean `workflowTaskExecutor`), per the doc's own MVP proposal — same
acknowledged limitation as the doc: no persistence of the queue itself, an in-flight job is lost
if the app restarts before a thread picks it up.

**Two deliberate deviations from the doc text / original, flagged up front**:
1. `04-RESTO-MODULI.md`'s Audit section lists "workflow execution" among audit-logged actions,
   but the original `workflow.engine.ts` never actually calls its audit logger — the only
   execution trail is the `WorkflowRun` row itself. This port follows the code (no `AuditService`
   call from `WorkflowEngine`), not the doc's prose, since `WorkflowRun` is already the dedicated
   log for this.
2. The original's `send_email` action hardcodes `from: 'noreply@gmasiero.it'` (the original
   author's personal domain) as a literal default. Not ported as a literal — it's a configurable
   property (`app.resend.from-email`, empty default) instead; if unset, the action skips with the
   same practical outcome as the already-existing "no `RESEND_API_KEY`" skip case.

- **Entities**: `Workflow` (`trigger`/`conditions`/`graph` as `Map<String,Object>`, `actions` as
  `List<Map<String,Object>>` — all JSON columns, never `JsonNode`, same Jackson rule as always).
  `WorkflowRun` (`status` enum `PENDING/RUNNING/SUCCESS/FAILED`, `input`/`steps` JSON,
  `error`/`finishedAt` nullable) — **no** `@OneToMany` back-collection on `Workflow`; the run list
  for a workflow is composed at the DTO level (`WorkflowDetailResponse(workflow, runs)`) via
  `WorkflowRunRepository.findTop20ByWorkflow_IdOrderByStartedAtDesc`, same compositional pattern
  `RecordDetailResponse` already uses for in/out `RecordLink`s (Phase 3) instead of a superfluous
  bidirectional relation. `Notification` is a **minimal entity pulled forward from Phase 6**
  (`04-RESTO-MODULI.md`) — only what the `notify_user` action needs to write (`userId`, `title`,
  `body`, `link`, `readAt`, `createdAt`); no list/mark-read endpoints yet, same treatment already
  given to `Page`/`AuditLog` in Phase 3.
- `ConditionEvaluator` (`services/`) — pure, stateless, two methods: `evaluate(conditions, record)`
  (operators `eq/neq/gt/lt/contains/is_set/in_days`, plus `lt` with the special `value:"today"`
  case; `{all:[...]}`/`{any:[...]}`/bare-single-rule fallback, same structural duck-typing as
  `FilterGroup` in Phase 3) and `interpolate(template, record)` (`{{record.x}}` /
  `{{record.x.y}}` substitution, `status` resolved from the column, everything else navigated
  inside `data`).
- `SafeUrlValidator` (`services/`) — the SSRF guard, a standalone static utility (`isSafe(url)`,
  not a Spring bean) tested in isolation per the doc's explicit requirement. Blocks non-http(s)
  schemes, `localhost`/`0.0.0.0`/`.local`/`.internal`, and the private/loopback/link-local IPv4
  ranges (`10.x`/`127.x`/`172.16-31.x`/`192.168.x`/`169.254.x`, including the cloud metadata
  endpoint `169.254.169.254`). **Known limitation inherited from the original, not fixed here**:
  no DNS-resolution check — a public hostname that resolves (or rebinds) to a private IP bypasses
  this guard, since only the literal hostname/IP text in the URL is inspected.
- `WorkflowActionExecutor` (`services/`) — one action per `type` (`update_record`, `create_record`,
  `create_link`, `create_task`/`create_reminder`, `create_calendar_event`, `notify_user`,
  `send_email`, `send_webhook`/`call_api`, `delay`, default), faithful port of
  `WorkflowEngine.execute()`. **Important fidelity note**: `create_record` and friends save
  directly via `RecordRepository`, not through `RecordsService.create()` — same as the original's
  direct Prisma write — so workflow-created records publish **no** `RecordCreatedEvent` and get
  **no** audit-log entry; a workflow does not cascade-trigger other workflows off its own writes
  (a conscious limitation of the original, not a porting bug). Records created by actions are
  **not** run through `RecordValidator.validate()` either, again matching the original. `send_webhook`
  checks `SafeUrlValidator.isSafe(url)` before any request, signs the body with HMAC-SHA256 (hex,
  via `hmacSha256Hex` — a new method, not a reuse of `JwtService.hashToken`, since that's an
  unkeyed digest and this needs a keyed MAC) when `action.secret` is set, using the exact same
  serialized bytes for both the signature and the request body. `delay` as an action **does not
  sleep** — it only returns `{delayed: ms}` — matching a vestigial case in the original where real
  sleeping only ever happens in `GraphWorkflowRunner`'s `delay` *node*, never in this linear
  switch.
- `GraphWorkflowRunner` (`services/`) — port of `graph-runner.ts`'s node walk (`trigger`/`action`/
  `condition`/`branch`/`loop`/`delay`), `MAX_STEPS=500` anti-infinite-loop guard preserved
  identically. **Simplification vs. the original**: the original injects the action executor and
  a `sleep` callback as functional parameters (testability workaround for a JS module context) —
  here `WorkflowActionExecutor` is just `@Autowired` directly, no functional parameters, same kind
  of simplification already used for `PageGeneratorService.generate`'s `isSystem` parameter in
  Phase 3 instead of duplicating logic across two call sites. `delay` nodes really do
  `Thread.sleep(ms)` (capped at 60s) since they already run on the dedicated `@Async` thread, not
  the HTTP request thread.
- `WorkflowEngine` (`services/`) — the orchestrator. `@EventListener`s on
  `RecordCreatedEvent`/`RecordUpdatedEvent`/`RecordDeletedEvent` dispatch to active workflows
  whose `trigger.type` matches (with `record.updated` events also matching a `field.changed`
  trigger); for `field.changed`, fires if **either** the named data field **or** the record's
  `status` column changed (`Objects.equals` on both, OR'd — needs `RecordUpdatedEvent.beforeStatus`,
  see above). `runScheduled()` (called hourly by `WorkflowScheduler`'s `@Scheduled(cron = "0 0 * *
  * *")`) polls all active `schedule`-trigger workflows against every record of their
  `trigger.objectKey` (or a single `null` record if no `objectKey`) — same "hourly poll over all
  scheduled workflows" approach as the doc proposes, not a per-workflow Spring cron registration.
  `runManual(workflowId, recordId)` returns `{queued:true}` immediately without waiting for
  execution, mirroring BullMQ's fire-and-forget enqueue semantics even without a real queue.
  **Testability seam**: `runAsync(UUID, Record)` is `@Async("workflowTaskExecutor")` and delegates
  to a package-private `runWorkflow(UUID, Record)` containing the real logic — tests call
  `runWorkflow` directly, bypassing the `@Async` proxy, same trick as `AnalyticsService`'s
  injectable `referenceDate` overload for deterministic tests. `runWorkflow` evaluates
  `wf.conditions` **before** creating any `WorkflowRun` row (no row at all if conditions fail,
  matching the original); on success/failure it persists `WorkflowRun` SUCCESS/FAILED as two
  separate `save()` calls (not one wrapping transaction) so each state transition is durable on
  its own — deliberately **not** `@Transactional`, partly because it wouldn't apply anyway
  (`runAsync` calls `runWorkflow` via self-invocation, bypassing the Spring AOP proxy) and partly
  because wrapping the whole execution — including any HTTP calls an action makes — in one long
  DB transaction would be wrong regardless. On a failed action, the exception is caught, the run
  persisted as FAILED with `error`, and logged via SLF4J — **not rethrown further**, unlike the
  original (which rethrows to feed BullMQ's `worker.on('failed', ...)` central logging); there's
  no separate worker process here to rethrow to, so the logging just happens synchronously in the
  same catch block.
- `WorkflowController` (`/api/workflows`) — full CRUD plus `POST /{id}/run`, `@RequirePerm(resource
  = "workflow", action = ...)` on every endpoint, `EXECUTE` specifically for the manual-run
  endpoint (the X of RBAC, explicitly required by the doc). `"workflow"` is already one of the 5
  fixed resources `PermissionSeeder` seeds — no seeder change needed, same as `"chart"` in Phase 4.
- `WorkflowSeeder` (`config/`, `@Order(4)`, after `PermissionSeeder`) ports the 12 default
  automations from `prisma/seed.ts` literally (Italian text unchanged) — per-name idempotent
  upsert (`findByName`, skip if present), same pattern as the original's `findFirst`-per-entry
  check, not a skip-if-table-non-empty check.
- **Out of scope this phase** (confirmed): notification list/mark-read endpoints, realtime push
  for notifications — Phase 6 per `04-RESTO-MODULI.md`.

### Relations (RecordLink CRUD — not itemized in any phase doc, added for parity)

Port of the original's `relations/relations.module.ts`, which exists in the reference repo but
isn't called out in any `md/` phase doc — `03-MOTORE-DINAMICO.md` only covers `RecordLink` as an
entity read embedded in `RecordDetailResponse`, never its own CRUD endpoints. Added to reach full
functional parity with the original.

- `RelationsController` (`controllers/`) is nested under the **same** `/api/records` prefix as
  `RecordsController`, not a separate base path — `GET/POST /{recordId}/links`,
  `DELETE /{recordId}/links/{linkId}`. No path collision: Spring's routing prioritizes literal
  segments (`/links`) over path-variables (`/{key}/{id}`), so this coexists cleanly with
  `RecordsController`'s `/{key}/{id}` mappings. The `DELETE` path keeps `recordId` as an unused
  path segment, matching the original's own URL shape (its handler doesn't bind it either).
- `RelationsService` has no `@RequirePerm` — same dynamic-RBAC pattern as `RecordsService`: a
  private `assertWrite(actor, recordId)` loads the `Record`, resolves its `objectType.key`, and
  calls `RbacService.resolve(..., PermAction.WRITE)` directly (not extracted into a shared utility
  with `RecordsService`'s analogous private `authorize()` — only two call sites, not worth it).
- `create()` **upserts** on the `(source_id, target_id, relation_key)` unique constraint already
  present on `RecordLink` (Phase 3) — same semantics as the original's Prisma `upsert`.
  `list(recordId)` merges `findBySource_Id` + `findByTarget_Id` (the original's single `OR` query),
  not exposed separately as in/out like `RecordDetailResponse` does.

### Users / Invite (completes Phase 1 — endpoints were never built)

`01-AUTH.md` specs the `Invite` entity but its endpoint list only covers
status/onboarding/login/refresh/logout/me — invite/accept/list/update/deactivate were a known,
explicitly-flagged gap (see this file's previous revisions). Ported from the original's
`users/users.module.ts`.

- `UserService`/`UserController` (`/api/users`): `list()` (maps `User`→`UserSummary`, always
  excludes `passwordHash` — same destructuring-omit the original does), `invite(actor, dto)`
  (404→400 if role key unknown or email already registered; generates a random hex token, stores
  only its SHA-256 hash via the new `HashUtil.sha256Hex` — see below — returns the raw token once
  for the admin to build the invite link, no email-sending integration here), `accept(dto)`
  (public endpoint, added to `SecurityConfig`'s `permitAll()` matcher list same as
  `/onboarding`/`/login`/`/refresh`; rejects invalid/expired/already-accepted tokens; hashes the
  new password via the **same `PasswordEncoder` (BCrypt) bean already used by `AuthService`** —
  the original uses argon2 here, not ported, since this codebase already standardized on BCrypt
  per `00-OVERVIEW.md`'s stack mapping), `update(id, dto)` (replaces `UserRole` rows wholesale —
  delete-all-then-recreate — when `roleKeys` is provided, same as the original), `deactivate(id)`
  (soft: `isActive=false` + revokes every active `Session` for that user, same security behavior
  as the original — not a real delete).
- New shared `services/HashUtil.java` — `sha256Hex(String)`, same SHA-256-then-hex-encode logic
  as `JwtService.hashToken`, factored out because `JwtService` stays scope-limited to the JWT
  access/refresh domain rather than becoming a generic hashing utility. Used by both
  `UserService` (invite tokens) and `ApiKeyService` (API keys, see below).

### ApiKey (Phase 6 — implemented)

Per `04-RESTO-MODULI.md`. Port of `api-keys/api-keys.module.ts`, plus the `X-Api-Key` → `Actor`
resolution that `02-RBAC.md` already specs the location of (an extension of
`JwtAuthenticationFilter`) but couldn't be implemented until the `ApiKey` entity existed —
previously flagged in this file as "modeled in `ActorType` but not resolved."

- `ApiKey` entity: the raw key (`nl_` + 24 random bytes hex) is **never persisted** — only
  `keyHash` (SHA-256 via `HashUtil`) and `prefix` (first 10 chars, for recognizing it in lists)
  survive creation. `ApiKeyService.create()` returns the raw key **once**, in the response body —
  same hard security constraint as the original: no way to ever view it again, only revoke
  (`revokedAt`) and reissue.
- `JwtAuthenticationFilter` now checks for `X-Api-Key` when there's no `Authorization: Bearer`
  header. If the key resolves to a non-revoked, non-expired `ApiKey`: sets a minimal Spring
  Security `SecurityContext` authentication (same `ROLE_USER` authority `UserPrincipal` already
  uses, just to satisfy `anyRequest().authenticated()`) **and** the `Actor` request attribute
  (`type=APIKEY`, `roleIds` from `apiKey.role` if set, else empty) — same "two parallel concepts"
  split `02-RBAC.md` describes between `UserDetails` (Spring Security's "is this authenticated")
  and `Actor` (custom RBAC's "what can this do"). Updates `lastUsedAt` on each successful
  resolution. No `SecurityConfig` matcher changes needed — API-key requests still go through
  `anyRequest().authenticated()`, just authenticated a different way.
- `ApiKeyController` (`/api/api-keys`) uses `@RequirePerm(resource = "apikey", ...)` — already one
  of the 5 fixed resources `PermissionSeeder` seeds, no seeder change needed.

### Webhooks (Phase 6 — implemented)

Per `04-RESTO-MODULI.md`. Port of `webhooks/webhooks.module.ts`.

- `Webhook` entity: `direction` (`INBOUND`/`OUTBOUND`), `secret` generated random hex at creation
  (never re-exposed), `events` as `List<String>` JSON (same JSON-column rule, not `JsonNode`).
- `WebhookController` (`/api/webhooks`) management endpoints (`GET`/`POST`/`DELETE`) use
  `@RequirePerm(resource = "workflow", ...)` — the original deliberately reuses the workflow RBAC
  resource for webhooks rather than a dedicated one, kept for fidelity (same quirk
  `ObjectsController` has with `"page"`). `POST /webhooks/in/{id}` is **public** (added to
  `SecurityConfig`'s matcher list) — the caller is an external system, not a Bearer/ApiKey
  client; it's protected by HMAC signature verification inside `WebhookService.receive()` instead.
- `WebhookService.receive()`: re-serializes the already-deserialized request body and recomputes
  `HMAC-SHA256(secret, json)` to compare against the `X-Signature` header — same approach (and
  same inherited fragility: if sender and receiver ever serialize JSON differently byte-for-byte,
  signatures could legitimately mismatch) as the original's `JSON.stringify(raw)` re-serialization;
  not "fixed" here since that would need raw-body capture, a bigger change the original doesn't
  make either. **One deliberate hardening deviation**: the signature comparison uses
  `MessageDigest.isEqual` (constant-time) instead of the original's direct `!==` string
  comparison, since this is a publicly-reachable endpoint comparing a security-sensitive value —
  same observable behavior, just timing-attack-resistant.
- `HmacUtil` (`services/HmacUtil.java`, static `sha256Hex(secret, body)`) — extracted from what
  was a private method on `WorkflowActionExecutor` (Phase 5's `send_webhook` action signing) once
  a second call site (`WebhookService`'s inbound verification) needed the identical algorithm.
  `WorkflowActionExecutorTest`'s HMAC vector test moved to `HmacUtilTest` accordingly.
- On successful verification, publishes `WebhookReceivedEvent` (`events/`) — **no listener yet**,
  same "event published, nothing subscribes" pattern as `RecordCreatedEvent` (Phase 3) and
  `NotifyEvent` (Phase 5).

### Files (Phase 6 — implemented)

Per `04-RESTO-MODULI.md`. Port of `files/files.module.ts`. Local disk storage, same as the
original — no S3/cloud storage.

- `FileObject` entity: `uploadedBy`/`recordId` are plain `UUID` columns, not JPA relations — same
  "soft FK, never navigated" pattern already used for `Record.ownerId`/`Notification.userId`.
- `FileStorageService.store()`: filename on disk is `UUID.randomUUID() + extension` (the
  original uses `randomBytes` instead — different RNG source, same anti-collision/
  anti-path-traversal goal, both unguessable); the original filename is preserved only in the
  `FileObject.filename` column, never used to construct the on-disk path.
  **Hardening deviation, declared**: `resolveOnDisk()` explicitly verifies the resolved path
  stays inside the configured upload directory (`Path.normalize()` + `startsWith()` check) before
  returning it for download — the original relies solely on the fact that it always generates the
  on-disk name itself; this adds a cheap, defensive runtime check on top, not a behavior change
  for any legitimately-stored file.
  `spring.servlet.multipart.max-file-size`/`max-request-size` set to 25 MB
  (`application.properties`), matching the original's Multer limit.
- `FileController` (`/api/files`): `POST` (multipart upload, query param `recordId?`) has **no**
  `@RequirePerm` — same as the original, gated only by authentication. `GET /{id}` streams the
  file back with `Content-Disposition: attachment` and a UTF-8-encoded filename
  (`ContentDisposition.attachment().filename(name, UTF_8)`) for correct Unicode/special-character
  handling, matching the original's `encodeURIComponent` intent.

### Notifications (Phase 6 — implemented)

Per `04-RESTO-MODULI.md`. Reuses the `Notification` entity already pulled forward minimally in
Phase 5 (for `WorkflowActionExecutor`'s `notify_user` action) — this phase only adds the
read/mark-read surface on top, no new entity.

- **No realtime** — the original uses a Socket.io gateway; `04-RESTO-MODULI.md` itself proposes,
  as the explicit MVP default, shipping without it and letting the frontend poll
  `GET /api/notifications` periodically. That's what's implemented: `GET`, `PATCH /read-all`,
  `PATCH /{id}/read`, no WebSocket/SSE. `NotifyEvent` (Phase 5) still has no listener.
- `NotificationService.markRead(actor, id)` mirrors the original's `updateMany({where:{id,
  userId}})` semantics: if the notification doesn't exist or belongs to someone else, it's a
  silent no-op, not an error — matches Prisma's "0 rows affected" behavior for an unmatched
  `updateMany`, not a Java-idiomatic 404.
- `NotificationController` has no `@RequirePerm` — notifications are always personal to the
  authenticated user, covered by `SecurityConfig`'s blanket `anyRequest().authenticated()`.

### Audit (Phase 6 — implemented, read-only)

Per `04-RESTO-MODULI.md`. Port of `audit/audit.module.ts`. No new writes — `AuditLog` (Phase 3,
`AuditService.log(...)`) is already written by `RecordsService` and now `UserService.invite`; this
phase only adds the read side.

- `GET /api/logs` (`AuditController`, `@RequirePerm(resource = "user", action = READ)` — same RBAC
  resource the original reuses: "whoever manages users can also see the audit trail"), optional
  `resource`/`resourceId` query params. `AuditQueryService` picks one of four
  `AuditLogRepository` finder methods (`findTop100By...OrderByCreatedAtDesc`, one per
  filter-combination: both/resource-only/resourceId-only/neither) instead of a single dynamic
  query — simpler than a query-builder for only 4 fixed combinations, same reasoning already used
  elsewhere in this codebase when the variant count is small.

### Hardening round (post-Phase-6 — implemented)

After all 6 phases + the two extra modules reached functional parity, a live smoke test plus a
full completeness/security audit surfaced a batch of real bugs and gaps, not part of any `md/`
phase doc. All items below are fixed and covered by unit tests; the first one was the most
impactful, found only through live HTTP testing, not static review.

- **`/error` blocked by Spring Security — masked every default-handled exception as a blank
  403** (the most significant finding). `HttpServletResponse.sendError()` — used internally by
  Spring's default `MethodArgumentNotValidException` handling (i.e. any failed `@Valid` on a
  request body with no custom handler) and by Tomcat for any genuinely uncaught exception —
  triggers an **internal forward to `/error`**, which re-enters the full Spring Security filter
  chain as a new, unauthenticated request. Without `/error` in the `permitAll()` list,
  `anyRequest().authenticated()` denied it with a blank 403, hiding the real intended status code
  (400/500/whatever) behind a generic, misleading 403 — for any exception not already handled by
  a custom `@RestControllerAdvice` returning a `ResponseEntity` directly (those bypass
  `sendError()` entirely, which is why `NotFoundException`/`BadRequestException`/etc. always
  worked correctly while `@Valid` failures and the `/me` NPE below did not). Fixed by adding
  `.requestMatchers("/error").permitAll()` in `SecurityConfig`, plus a new
  `exceptions/GlobalExceptionAdvice.java` (`@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`)
  that gives `MethodArgumentNotValidException` a proper 400 with per-field messages, and a
  catch-all `Exception.class` → 500 "Errore interno" (logged via SLF4J) as a last-resort safety
  net instead of silently falling through to Spring Boot's default behavior.
  **Second-order bug this introduced**: `ExceptionHandlerExceptionResolver` resolves
  `@ControllerAdvice` beans by **bean order, stopping at the first bean with any applicable
  handler** — it does not scan every bean for the most specific match. Beans with no explicit
  `@Order` default to `Ordered.LOWEST_PRECEDENCE`, same as the new catch-all, so ties were broken
  by incidental bean-scan order — which let `GlobalExceptionAdvice`'s `Exception.class` handler
  shadow `BadRequestException` (`/me` with an `X-Api-Key` started returning 500 instead of 400).
  Fixed by adding `@Order(0)` to the three pre-existing advice classes (`AuthException`,
  `RecordExceptionAdvice`, `RbacExceptionAdvice`) to guarantee they're evaluated before the
  catch-all.
- **Onboarding accepted any password length** — `OnboardingRequest.password` had no `@Size`
  constraint, unlike `AcceptInviteDto`'s. Added the same `@Size(min = 8, ...)`.
- **`ownerId` was client-controlled even under `OWN` RBAC scope** — `RecordsService.create()`/
  `update()` honored any client-supplied `ownerId`, letting a user with only `OWN`-scoped write
  permission create or reassign records they don't own, defeating the scope's purpose. Fixed via
  a private `resolveOwnerId(...)` that forces `ownerId = actor.id()` whenever `scope ==
  PermScope.OWN && actor.type() == ActorType.USER`, ignoring any client value in that case; `ALL`/
  `TEAM` scope behavior (client value wins, else default to actor) is unchanged.
- **`/api/auth/me` threw an NPE when called with `X-Api-Key`** — `AuthService.me(UserPrincipal)`
  assumed a non-null principal, but the API-key auth path (`JwtAuthenticationFilter`) sets a
  minimal `Authentication` whose principal is a `String` (the API key id), not a `UserPrincipal`
  — Spring's `@AuthenticationPrincipal` resolver silently returns `null` on that type mismatch
  rather than throwing. Added an explicit null-guard at the top of `me()` throwing
  `BadRequestException` with a clear message ("this endpoint requires a JWT-authenticated user,
  not an API key") instead of an unhandled NPE.
- **`GET /api/webhooks` leaked `Webhook.secret`** — the HMAC signing secret was serialized
  in plain JSON on every list call. Added `dtos/WebhookSummary.java` (everything except
  `secret`) and switched `WebhookService.list()`/`WebhookController.list()` to return it;
  `create()` still returns the full entity once, by design (the only time the secret is needed).
- **No CORS policy** — the app had no `CorsConfigurationSource` at all, relying on whatever the
  browser/Spring default did. Added an explicit allowlist
  (`app.cors.allowed-origins`, env `CORS_ALLOWED_ORIGINS`, defaults to the Angular dev server)
  via a `CorsConfigurationSource` bean wired first in the `SecurityConfig` filter chain.
  Deliberately never a wildcard origin: requests carry credentials (`Authorization`/`X-Api-Key`),
  and CORS forbids combining a wildcard origin with `allowCredentials(true)`.
- **No rate limiting anywhere** — `/api/auth/login`, `/api/auth/refresh`, and
  `/api/users/accept-invite` had no brute-force/abuse protection. Added `security/RateLimiter.java`
  (fixed-window, in-memory, per `servletPath:remoteAddr` key, `app.rate-limit.max-attempts`/
  `app.rate-limit.window-seconds` configurable, defaults 10/60s) and `security/RateLimitFilter.java`
  (`OncePerRequestFilter`, only applies to POST on the three listed paths, registered before
  `JwtAuthenticationFilter`, returns a literal 429 with a JSON body — `HttpServletResponse` has no
  named constant for 429). **Known limitation, documented not fixed**: in-memory only, so this
  only protects a single instance; a multi-instance deployment needs a shared store (Redis etc.)
  instead.
- **`SafeUrlValidator`'s SSRF guard had no DNS-rebinding protection** — the existing literal-IPv4
  regex check only inspected the URL's literal hostname/IP text, so a public hostname that
  resolves (or rebinds) to a private/loopback/link-local address bypassed it entirely (a
  limitation explicitly flagged as inherited-but-not-fixed when `send_webhook` was first built in
  Phase 5). Now resolves the hostname via `InetAddress.getAllByName(...)` and checks **every**
  resolved address with `isAnyLocalAddress()/isLoopbackAddress()/isLinkLocalAddress()/
  isSiteLocalAddress()`, in addition to the pre-existing literal check. Same testability-seam
  pattern used elsewhere in the codebase (`AnalyticsService.referenceDate`,
  `WorkflowEngine.runWorkflow`): public `isSafe(url)` delegates to a package-private `isSafe(url,
  DnsResolver resolver)`, so tests inject a fake resolver instead of doing real DNS lookups (note:
  `InetAddress.getAllByName(...)` does *not* perform network I/O when given a literal IP string,
  only for actual hostnames — existing literal-IP tests are unaffected and still use the 1-arg
  facade).
- **File upload accepted any extension** — `FileStorageService.store()` had no extension
  allow/deny list, so executable/script files (`.exe`, `.sh`, `.bat`, `.jsp`, etc.) could be
  uploaded and later served back via `GET /api/files/{id}`. Added a `BLOCKED_EXTENSIONS` set (24
  dangerous extensions) checked up front, throwing `BadRequestException` on a match.

None of these were caught by the project's pure-Mockito unit test suite before this round — the
`/error` bug and its second-order advice-ordering bug in particular are cross-cutting Spring MVC/
Security framework behavior that's invisible without a real Spring context and a live HTTP
request, reinforcing why live smoke testing (not just `./gradlew test`) is part of this project's
verification routine before considering a phase done.
