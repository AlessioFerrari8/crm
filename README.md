# CRM

CRM per aziende italiane. Anagrafiche, preventivi e fatture con righe e IVA per aliquota, ticket, report, dashboard, workflow automatici e stampa PDF.

L'architettura è **metadata-driven**: non esistono tabelle `contatti` o `aziende`. Esiste un'unica entità `Record`, il cui `objectType` dice che cosa sia e i cui campi vivono in una colonna `jsonb`. La forma dei campi è descritta da righe `ObjectType`/`FieldDef`, non dallo schema SQL. Aggiungere un modulo non richiede una migrazione, richiede semplicemente un record di configurazione.

Le uniche due eccezioni volute sono le righe documento (`document_line`) e i totali, che sono colonne vere perché ci si fanno somme.

## Stack 

| Parte      | Tecnologia                                               |
|------------|----------------------------------------------------------|
| Backend    | Java 21, Spring Boot 4.1, Spring Security, Hibernate 7   |
| Database   | PostgreSQL 16 (`jsonb`, `pg_trgm`)                       |
| Frontend   | Angular 22 standalone, Tailwind 4 + daisyUI, Chart.js    |
| PDF        | Thymeleaf (template su DB) + openhtmltopdf               |
| Build      | Gradle wrapper (backend), npm (frontend), Docker Compose |

## Avvio rapido, con Docker

```bash
cp backend/.env.example backend/.env     # e riempi i segreti, vedi sotto
docker compose up -d --build
```

- frontend  http://localhost:4200
- backend   http://localhost:8080
- Swagger   http://localhost:8080/swagger-ui.html
- Postgres  localhost:5433 (utente `user`, password `user`, database `crm_db`)

Alla prima apertura il frontend mostra l'**onboarding**: crea il workspace e il primo utente, che prende il ruolo `owner`. L'endpoint si chiude da solo dopo il primo uso.

## Avvio in sviluppo

Database dal compose, applicazioni in locale:

```bash
docker compose up -d postgres-db

cd backend  && ./gradlew bootRun         # http://localhost:8080
cd frontend && npm install && npm start  # http://localhost:4200
```

`application.properties` punta a `localhost:5433`, cioe' al Postgres del compose: `DB_USERNAME`/`DB_PASSWORD` vanno messi a `user`/`user`, non ai `postgres` di default.

Lo schema lo crea Hibernate (`ddl-auto=update`). Non c'e' Flyway: le colonne nuove su tabelle già popolate vogliono un `@ColumnDefault`, o l'avvio fallisce.

## Variabili d'ambiente

Tutte in `backend/.env` (gitignorato). Il modello completo è in `backend/.env.example`. Le quattro senza default, cioè quelle senza cui l'app non parte:

| Variabile            | A cosa serve                                         |
|----------------------|------------------------------------------------------| 
| `JWT_ACCESS_SECRET`  | firma degli access token (15 min)                    |
| `JWT_REFRESH_SECRET` | firma dei refresh token (30 giorni), segreto diverso |
| `DB_USERNAME`        | utente Postgres                                      |
| `DB_PASSWORD`        | password Postgres                                    |

Si generano con `openssl rand -base64 32`, uno diverso per ciascuno. Non hanno un default apposta: un segreto scritto nel repo è un segreto pubblico.

Le altre (email, CORS, cookie, rate limit, Cal.com, Swagger) hanno tutte un default sensato per lo sviluppo locale: vedi `.env.example`.

## Test

```bash
cd backend && ./gradlew test
```

La maggior parte sono Mockito puro e non serve niente. Le classi che estendono `AbstractIntegrationTest` avviano un `postgres:16-alpine` usa e getta con Testcontainers: **serve Docker acceso**, non un database.

Non incanalare mai l'output in `tail`: l'exit code diventa quello di `tail` e un fallimento passa inosservato.

Il frontend:

```bash
cd frontend && npx ng build      # il collaudo vero
npm test                         # vitest
```

## Struttura

```
backend/src/main/java/it/northleap/backend/
  config/        seeder (@Order 1..7) e configurazione Spring
  controllers/   un file per risorsa HTTP
  services/      la logica: e' qui che sta il progetto
  repositories/  Spring Data
  entities/      JPA. Le colonne jsonb sono Map/List, mai JsonNode
  dtos/          quello che entra ed esce dall'API
  security/      JWT, cookie, RBAC, rate limit
frontend/src/app/
  pages/         una cartella per schermata
  components/    quello che si riusa
  services/      un service per risorsa, @Service() (Angular 22)
  core/          interceptor, guard, configurazione
docker/init.sql  estensioni Postgres (pg_trgm), solo alla prima creazione
```

Le cartelle sono per **tipo**, non per feature: un controller nuovo va in `controllers/`, non accanto al suo service.

## Sicurezza, in breve

- **Autenticazione a cookie httpOnly** (`access_token`, `refresh_token`): i token 
  non sono mai nel body della risposta e JavaScript non li legge.
- **CSRF** double-submit con cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`, escluso
  solo per i webhook in ingresso e i form pubblici, che sono protetti da firma
  HMAC e rate limit.
- **RBAC** per risorsa e per azione (lettura / scrittura / esecuzione) con tre
  ambiti: `OWN`, `TEAM`, `ALL`. Il controllo sui record dinamici sta nel service,
  non in un'annotazione, perche' la risorsa e' il nome del modulo a runtime.
- **API key** `nl_...` per le integrazioni: in database c'e' solo l'hash, la
  chiave in chiaro si vede una volta sola.
- **Rate limit** in memoria per IP su login, refresh, invito e webhook. È per
  singola istanza: con più istanze serve uno store condiviso.
- **Upload**: estensioni pericolose bloccate, nome su disco generato dal server,
  percorso verificato dentro la cartella di upload.
- **SSRF**: gli URL chiamati dai workflow passano da un validatore che risolve il
  DNS e rifiuta indirizzi privati, loopback e link-local.

## Cose che sorprendono, e sono volute

- **Le colonne `jsonb` non sono mai `JsonNode`.** Hibernate 7 le tratta con
  Jackson 2, Spring MVC serializza con Jackson 3: un tipo specifico di libreria
  esplode a runtime. `Map<String,Object>` e `List<...>` funzionano con entrambi.
- **`Record` va importato esplicitamente.** Senza import, `Record` è
  `java.lang.Record` e l'errore parla di metodi che esistono.
- **Le view del PDF sono classi, non `record`.** Thymeleaf cerca `getX()`, un
  record espone `x()`: il campo esce vuoto, senza errori.
- **I totali di un documento li calcola un posto solo**, `DocumentTotalService`,
  nell'ordine: riga, sconto riga, sconto testata, trasporto, IVA per aliquota,
  totale. Il PDF e la fattura elettronica leggono quei numeri, non li ricalcolano.
- **I seeder hanno un `@Order` esplicito** (1 Role, 2 ObjectType, 3 Permission,
  4 Workflow, 5 Inventory, 6 PdfTemplate, 7 Report). Due `@Order` uguali non
  danno errore: danno un avvio non deterministico.
- **Il Dockerfile del backend costruisce con `bootJar -x test`**, perché dentro
  l'immagine i test non possono girare (vogliono Docker). La regola e' lanciare
  `./gradlew build` prima di `docker compose build`, sempre.
