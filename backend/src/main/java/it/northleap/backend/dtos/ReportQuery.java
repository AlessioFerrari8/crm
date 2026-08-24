package it.northleap.backend.dtos;

import java.util.List;

public record ReportQuery(
        String objectKey,   // modulo principale
        FilterGroup filter, // stesso formato del Filter Builder già esistente
        List<Dimension> rows,   // raggruppamenti di fila
        List<Dimension> columns,    // raggruppamenti di colonna (= pivot, se non vuoto)
        List<Metric> metrics,   // cosa si calcola
        String orderBy, // chiave di ordinamento del risultato
        boolean orderDesc,
        Integer limit
) {

    // raggruppamento
    // field = chiave del campo (dentro dato) oppure una colonna vera (status, owner_id, created_at)
    // grain serve solo per le date: MONTH è quello dei report "ultimi 12 mesi"
    public record Dimension(String field, Grain grain) {}

    public enum Grain { NONE, DAY, WEEK, MONTH, QUARTER, YEAR }


    // COUNT Non usa field. AVG_DAYS_BETWEEN è la famiglia "giorni medi di chisura / tempo
    // di attraversamento": due campi data, media della differenza in giorni
    public record Metric(Aggregate aggregate, String field, String secondField, String label) {}

    public enum Aggregate { COUNT, SUM, AVG, MIN, MAX, AVG_DAYS_BETWEEN }
}
