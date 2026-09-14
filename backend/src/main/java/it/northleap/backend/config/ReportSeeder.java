package it.northleap.backend.config;


import it.northleap.backend.entities.Report;
import it.northleap.backend.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// @Order dopo i seeder esistenti (1 Role, 2 ObjectType, 3 Permission, 4 Workflow,
// 5 Inventory, 6 PdfTemplate)
// idempotente sul titolo, al riavvio non duplica e un report che l'utente ha cancellato apposta non torna in vita
@Component
@Order(7)
@RequiredArgsConstructor
public class ReportSeeder implements ApplicationRunner {

    private final ReportRepository reportRepository;

    @Override
    public void run(ApplicationArguments args) {
        seed("TICKET - Aperti per mese (ultimi 12 mesi)", Report.ReportRender.BAR, "ticket", "Supporto",
                Map.of("objectKey", "ticket",
                        "filter", group("and", List.of(
                                cond("status", "eq", "open"))),
                        "rows", List.of(dim("createdAt", "MONTH")),
                        "metrics", List.of(metric("COUNT", null, null, "Numero")),
                        "orderDesc", false));

        seed("TICKET - Non chiusi per assegnatario", Report.ReportRender.GRID, "ticket", "Supporto",
                Map.of("objectKey", "ticket",
                        "filter", group("and", List.of(cond("status", "ne", "closed"))),
                        "rows", List.of(dim("assignee", "NONE")),
                        "metrics", List.of(metric("COUNT", null, null, "Numero")),
                        "orderBy", "met0", "orderDesc", true));

        seed("TICKET - Giorni medi per chiusura (ultimi 12 mesi)", Report.ReportRender.LINE, "ticket", "Supporto",
                Map.of("objectKey", "ticket",
                        "rows", List.of(dim("createdAt", "MONTH")),
                        "metrics", List.of(metric("AVG_DAYS_BETWEEN", "createdAt", "closedAt", "Giorni")),
                        "orderDesc", false));

        seed("OPPORTUNITA' - Numero e valore per stadio", Report.ReportRender.BAR, "opportunity", "Vendite",
                Map.of("objectKey", "opportunity",
                        "rows", List.of(dim("stage", "NONE")),
                        "metrics", List.of(metric("COUNT", null, null, "Numero"),
                                metric("SUM", "amount", null, "Importo")),
                        "orderDesc", false));

        seed("OPPORTUNITA' - Stadio per mese di chiusura attesa", Report.ReportRender.GRID, "opportunity", "Vendite",
                Map.of("objectKey", "opportunity",
                        "rows", List.of(dim("stage", "NONE")),
                        "columns", List.of(dim("closeDate", "MONTH")),
                        "metrics", List.of(metric("COUNT", null, null, "Numero"),
                                metric("SUM", "amount", null, "Importo")),
                        "orderDesc", false));

        seed("LEAD - Per fonte", Report.ReportRender.PIE, "lead", "Vendite",
                Map.of("objectKey", "lead",
                        "rows", List.of(dim("source", "NONE")),
                        "metrics", List.of(metric("COUNT", null, null, "Numero")),
                        "orderBy", "met0", "orderDesc", true));
    }

    private void seed(String title, Report.ReportRender render, String objectKey, String folder,
                      Map<String, Object> query) {
        if (reportRepository.existsByTitle(title)) return;
        Report r = new Report();
        r.setTitle(title);
        r.setRender(render);
        r.setObjectKey(objectKey);
        r.setFolder(folder);
        r.setOwnerId(null); // condivisi, report di sistema
        r.setQuery(query);
        reportRepository.save(r);
    }

    // helper per non riscrivere le Map a mano, rendono le righe sopra leggibili
    private Map<String, Object> dim(String field, String grain) {
        return Map.of("field", field, "grain", grain);
    }

    private Map<String, Object> metric(String aggregate, String field, String secondField, String label) {
        // HashMap e non Map.of: Map.of non accetta valori null, e field è null per count
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("aggregate", aggregate);
        m.put("field", field);
        m.put("secondField", secondField);
        m.put("label", label);
        return m;
    }

    private Map<String, Object> cond(String field, String op, Object value) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("field", field);
        m.put("op", op);
        m.put("value", value);
        return m;
    }

    private Map<String, Object> group(String combinator, List<Map<String, Object>> conditions) {
        return Map.of("combinator", combinator, "conditions", conditions);
    }
}