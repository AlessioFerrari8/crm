package it.northleap.backend.dtos;

import java.time.Instant;
import java.util.List;

// "Importo: da 900,00 a 1100,00" si legge come una storia, { "total": 1100 } sembra un log
public record HistoryEntry(
        String action,
        String author,
        Instant at,
        List<Change> changes
) {
    public record Change(String field, String from, String to) { }

    // DTO nostro e non Page di Spring Data: la forma JSON di PageImpl è cambiata fra una versione e l'altra
    public record Page(List<HistoryEntry> items, int page, int totalPages) { }
}
