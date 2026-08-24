package it.northleap.backend.controllers;


import it.northleap.backend.dtos.CreateDashboardDto;
import it.northleap.backend.dtos.CreateDashboardTabDto;
import it.northleap.backend.dtos.CreateWidgetDto;
import it.northleap.backend.dtos.DashboardResponse;
import it.northleap.backend.entities.Dashboard;
import it.northleap.backend.entities.DashboardTab;
import it.northleap.backend.entities.DashboardWidget;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<List<Dashboard>> list(@CurrentActor Actor actor) {
        return ResponseEntity.ok(dashboardService.list(actor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DashboardResponse> get(@CurrentActor Actor actor, @PathVariable UUID id) {
        return ResponseEntity.ok(dashboardService.get(actor, id));
    }

    @PostMapping
    public ResponseEntity<Dashboard> create(@CurrentActor Actor actor,
                                            @Valid @RequestBody CreateDashboardDto dto) {
        return ResponseEntity.ok(dashboardService.create(actor, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@CurrentActor Actor actor, @PathVariable UUID id) {
        dashboardService.remove(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tabs")
    public ResponseEntity<DashboardTab> addTab(@CurrentActor Actor actor, @PathVariable UUID id, @Valid
                                               @RequestBody CreateDashboardTabDto dto) {
        return ResponseEntity.ok(dashboardService.addTab(actor, id, dto));
    }

    @DeleteMapping("/tabs/{tabId}")
    public ResponseEntity<Void> removeTab(@CurrentActor Actor actor, @PathVariable UUID tabId) {
        dashboardService.removeTab(actor, tabId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tabs/{tabId}/widgets")
    public ResponseEntity<DashboardWidget> addWidget(@CurrentActor Actor actor, @PathVariable UUID tabId,
                                                     @Valid @RequestBody CreateWidgetDto dto) {
        return ResponseEntity.ok(dashboardService.addWidget(actor, tabId, dto));
    }

    @PutMapping("/widgets/{widgetId}")
    public ResponseEntity<DashboardWidget> updateWidget(@CurrentActor Actor actor,
                                                        @PathVariable UUID widgetId,
                                                        @Valid @RequestBody CreateWidgetDto dto) {
        return ResponseEntity.ok(dashboardService.updateWidget(actor, widgetId, dto));
    }

    @DeleteMapping("/widgets/{widgetId}")
    public ResponseEntity<Void> removeWidget(@CurrentActor Actor actor, @PathVariable UUID widgetId) {
        dashboardService.removeWidget(actor, widgetId);
        return ResponseEntity.noContent().build();
    }
}


// nota: /api/dashboards/tabs/{tabId} e /api/dashboards/{id}/tabs
// queste rotte convivono senza ambiguità perché Spring preferisce il segmento letterale ("tabs") alla
// path variable {id}
