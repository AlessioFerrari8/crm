package it.northleap.backend.controllers;


import it.northleap.backend.dtos.CreateReportDto;
import it.northleap.backend.dtos.ReportQuery;
import it.northleap.backend.dtos.ReportResult;
import it.northleap.backend.entities.Report;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.ReportEngine;
import it.northleap.backend.services.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// @RequirePerm su "chart" come ChartController: un report è un grafico con un motore più serio, e "chart"
// è già una delle 5 risorse fisse che PermissionSeeder semina -> nessuna modifica al seeder.
// Il permesso PER MODULO (posso leggere i ticket?) lo controlla ReportService: qui
// l'objectKey non si conosce ancora
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportEngine reportEngine;

    @GetMapping
    @RequirePerm(resource = "chart", action = PermAction.READ)
    public ResponseEntity<List<Report>> list(@CurrentActor Actor actor) {
        return ResponseEntity.ok(reportService.list(actor));
    }

    @PostMapping
    @RequirePerm(resource = "chart", action = PermAction.WRITE)
    public ResponseEntity<Report> create(@CurrentActor Actor actor, @Valid @RequestBody CreateReportDto dto) {
        return ResponseEntity.ok(reportService.create(actor, dto));
    }

    @PostMapping("/{id}")
    @RequirePerm(resource = "chart", action = PermAction.WRITE)
    public ResponseEntity<Report> update(@CurrentActor Actor actor, @PathVariable UUID id,
                                         @Valid @RequestBody CreateReportDto dto) {
        return ResponseEntity.ok(reportService.update(actor, id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(resource = "chart", action = PermAction.WRITE)
    public ResponseEntity<Void> remove(@CurrentActor Actor actor, @PathVariable UUID id) {
        reportService.remove(actor, id);
        return ResponseEntity.noContent().build();
    }

    // post e non get anche per l'esecuzione di un report salvato: è un'operazione costosa
    // e non cacheabile, e la simmetria con /run "al volo", che il body ha per forza, vale di più
    // della purezza REST
    @PostMapping("/{id}/run")
    @RequirePerm(resource = "chart", action = PermAction.READ)
    public ResponseEntity<ReportResult> run(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(reportService.run(actor, id));
    }

    // anteprima del wizard: query senza salvare nulla
    @PostMapping("/{id}/duplicate")
    @RequirePerm(resource = "chart", action = PermAction.WRITE)
    public ResponseEntity<Report> duplicate(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(reportService.duplicate(actor, id));
    }

    @GetMapping("/{id}/export")
    @RequirePerm(resource = "chart", action = PermAction.READ)
    public ResponseEntity<Map<String, Object>> export(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(reportService.export(actor, id));    }


    @PostMapping("/import")
    @RequirePerm(resource = "chart", action = PermAction.WRITE)
    public ResponseEntity<Report> importDefinition(@CurrentActor Actor actor, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(reportService.importDefinition(actor, body));
    }

    // esegue una query non salvata: anteprima del wizard e motore dei widget
    // Un widget è un report non salvato, con un rendering diverso
    @PostMapping("/run")
    @RequirePerm(resource = "chart", action = PermAction.READ)
    public ResponseEntity<ReportResult> runAdhoc(@CurrentActor Actor actor, @Valid @RequestBody ReportQuery query) {
        return ResponseEntity.ok(reportService.preview(actor, query));
    }
}
