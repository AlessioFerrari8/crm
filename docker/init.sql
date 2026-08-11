-- Estensioni Postgres che l'applicazione da' per scontate.
--
-- Gira una sola volta, quando il container crea il database da zero: docker-compose lo monta
-- in /docker-entrypoint-initdb.d/, che l'entrypoint di postgres esegue come superuser SOLO se
-- la directory dati e' vuota. Su un volume postgres_data gia' esistente NON viene rieseguito:
-- in quel caso vanno lanciate a mano, una volta, con
--     docker compose exec postgres-db psql -U user -d crm_db -f /docker-entrypoint-initdb.d/init.sql
--
-- Non e' un seeder dell'app apposta: CREATE EXTENSION richiede il superuser, e l'utente con cui
-- gira il backend non deve averlo.

-- pg_trgm serve a similarity(), usata da RecordRepository.findDuplicates (ricerca duplicati /
-- merge). Senza, la query fallisce con
--   ERROR: function similarity(character varying, character varying) does not exist
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- L'indice trigram NON puo' stare qui: questo script gira prima che Hibernate (ddl-auto) crei
-- la tabella "record", quindi fallirebbe. Va lanciato una volta dopo il primo avvio del backend:
--
--     CREATE INDEX IF NOT EXISTS idx_record_title_trgm
--         ON record USING gin (title gin_trgm_ops);
--
-- Non e' obbligatorio per la correttezza, ma findDuplicates fa un self join su record e senza
-- indice il costo cresce col quadrato delle righe.
