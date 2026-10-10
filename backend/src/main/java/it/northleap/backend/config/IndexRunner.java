package it.northleap.backend.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// gli indici che ddl-auto non sa creare (espressioni, parziali, GIN). Qui non c'è Flyway: questo runner gira dopo
// ddl-auto, quindi le tabelle esistono, e IF NOT EXISTS rende ogni riavvio un no-op
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class IndexRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    private static final List<String> DDL = List.of(
            // ogni report è una scasione completa
            "CREATE EXTENSION IF NOT EXISTS pg_trgm",
            "CREATE INDEX IF NOT EXISTS idx_record_data_gin ON record USING gin (data jsonb_path_ops)",
            "CREATE INDEX IF NOT EXISTS idx_record_type_created ON record (object_type_id, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_record_title_trgm ON record USING gin (title gin_trgm_ops)",
            // la ricerca full-text. to_tsvector(config, jsonb) indicizza solo i valori di tipo stringa, non le
            // chiavi: con data::text cercare "company" troverebbe ogni record che ha quel campo
            "CREATE INDEX IF NOT EXISTS idx_record_data_fts ON record USING gin (to_tsvector('italian', data))",
            // l'invariante che oggi vive dentro replaceAll lo garantisce il database
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_document_line_no ON document_line (document_id, line_no)",
            //un solo timer attivo per utente. Due tab aperte aggirano qualunque controllo nel service
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_time_entry_running ON time_entry (user_id) WHERE ended_at IS NULL",
            // la coda è la tabella, e si legge per stato
            "CREATE INDEX IF NOT EXISTS idx_campaign_recipient_pending ON campaign_recipient (campaign_id) WHERE status = 'PENDING'"
    );

    @Override
    public void run(ApplicationArguments args) {
        for (String ddl : DDL) {
            try {
                jdbc.execute(ddl);
            } catch(Exception e) {
                // un indice che non si crea (tabella di una fase non ancora scritta, estensione senza permessi) non deve
                // impedire l'avvio: si logga e si va avanti
                log.warn("Index DDL skipped: {} ({})", ddl, e.getMessage());
            }
        }
    }
}
