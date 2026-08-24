package it.northleap.backend.dtos;

import it.northleap.backend.entities.Dashboard;
import it.northleap.backend.entities.DashboardTab;
import it.northleap.backend.entities.DashboardWidget;

import java.util.List;
import java.util.UUID;

// La dashboard completa in una chiamata: tab e widget insieme. È l'unica cosa che il frontend
// chiede al caricamento della pagina; i DATI dei widget no, quelli li chiede ogni widget
// per conto suo
public record DashboardResponse(
        UUID id,
        String name,
        UUID ownerId,
        List<String> roleIds,
        int sortOrder,
        List<TabResponse> tabs
) {
    public record TabResponse(
            UUID id,
            String label,
            int sortOrder,
            List<DashboardWidget> widgets
    ) {

    }

    public static DashboardResponse of(Dashboard d, List<DashboardTab> tabs,
                                       java.util.Map<UUID, List<DashboardWidget>> widgetsByTab) {
        return new DashboardResponse(d.getId(), d.getName(), d.getOwnerId(), d.getRoleIds(), d.getSortOrder(),
                tabs.stream()
                        .map(t -> new TabResponse(t.getId(), t.getLabel(), t.getSortOrder(),
                                widgetsByTab.getOrDefault(t.getId(), List.of())))
                        .toList());
    }
}
