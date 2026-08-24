package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateDashboardDto;
import it.northleap.backend.dtos.CreateDashboardTabDto;
import it.northleap.backend.dtos.CreateWidgetDto;
import it.northleap.backend.dtos.DashboardResponse;
import it.northleap.backend.entities.Dashboard;
import it.northleap.backend.entities.DashboardTab;
import it.northleap.backend.entities.DashboardWidget;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.DashboardRepository;
import it.northleap.backend.repositories.DashboardTabRepository;
import it.northleap.backend.repositories.DashboardWidgetRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final DashboardTabRepository tabRepository;
    private final DashboardWidgetRepository widgetRepository;
    private final RbacService rbacService;

    // Le dashboard che l'utente vede: le sue, più le condivise il cui elenco ruoli è
    // vuoto o contiene uno dei suoi. Il filtro sui ruoli sta qui e non in SQL perché roleIds
    // è un jsonb e le dashboard sono decine, non milioni
    public List<Dashboard> list(Actor actor) {
        List<String> mine = actor.roleIds().stream().map(UUID::toString).toList();
        return dashboardRepository.findByOwnerIdIsNullOrOwnerIdOrderBySortOrderAscNameAsc(actor.id())
                .stream()
                .filter(d -> d.getOwnerId() != null
                || d.getRoleIds() == null
                || d.getRoleIds().isEmpty() || d.getRoleIds().stream().anyMatch(mine::contains)).toList();
    }

    // La dashboard intera: tab + widget, una chiamata sola. Due query e non una con le JOIN fetch, sono tre
    // livelli e la fetch join multipla su due @OneToMany fa esplodere il prodotto cartesiano
    @Transactional(readOnly = true)
    public DashboardResponse get(Actor actor, UUID id) {
        Dashboard dashboard = load(actor, id);
        List<DashboardTab> tabs = tabRepository.findByDashboard_IdOrderBySortOrderAsc(id);
        Map<UUID, List<DashboardWidget>> widgets = tabs.stream()
                .collect(Collectors.toMap(DashboardTab::getId,
                        t -> widgetRepository.findByTab_IdOrderBySortOrderAsc(t.getId())));

        return DashboardResponse.of(dashboard, tabs, widgets);
    }

    @Transactional
    public Dashboard create(Actor actor, CreateDashboardDto dto) {
        if (!dto.personal()) assertCanShare(actor);
        Dashboard d = new Dashboard();
        d.setName(dto.name().trim());
        d.setOwnerId(dto.personal() ? actor.id() : null);
        d.setRoleIds(dto.roleIds() != null ? dto.roleIds() : List.of());
        d.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        Dashboard saved = dashboardRepository.save(d);

        // Una dashboard senza tab non è visualizzabile: il primo lo creo io
        // È la stessa idea di PageGeneratorService, che genera la pagina insieme all'ObjectType invece di
        // lasciare l'utente davanti a un vuoto.
        DashboardTab first = new DashboardTab();
        first.setDashboard(saved);
        first.setLabel("Generale");
        first.setSortOrder(0);
        tabRepository.save(first);
        return saved;
    }

    @Transactional
    public void remove(Actor actor, UUID id) {
        Dashboard d = load(actor, id);
        assertOwner(actor, d);
        // niente cascade sulle entità: le cancellazioni sono esplicite, così si vede in che
        // ordine avvengono (widget -> tab -> dashboard)
        tabRepository.findByDashboard_IdOrderBySortOrderAsc(id)
                .forEach(t -> widgetRepository.deleteByTab_Id(t.getId()));

        tabRepository.deleteByDashboard_Id(id);
        dashboardRepository.delete(d);
    }

    @Transactional
    public DashboardTab addTab(Actor actor, UUID dashboardId, CreateDashboardTabDto dto) {
        Dashboard d = load(actor, dashboardId);
        assertOwner(actor, d);
        DashboardTab tab = new DashboardTab();
        tab.setDashboard(d);
        tab.setLabel(dto.label().trim());
        tab.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() :
                tabRepository.findByDashboard_IdOrderBySortOrderAsc(dashboardId).size());

        return tabRepository.save(tab);
    }

    @Transactional
    public void removeTab(Actor actor, UUID tabId) {
        DashboardTab tab = tabRepository.findById(tabId)
                .orElseThrow(() -> new NotFoundException("Tab not found"));

        assertOwner(actor, load(actor, tab.getDashboard().getId()));
        widgetRepository.deleteByTab_Id(tabId);
        tabRepository.delete(tab);
    }

    @Transactional
    public DashboardWidget addWidget(Actor actor, UUID tabId, CreateWidgetDto dto) {
        DashboardTab tab = tabRepository.findById(tabId)
                .orElseThrow(() -> new NotFoundException("Tab not found"));
        assertOwner(actor, load(actor, tab.getDashboard().getId()));

        DashboardWidget w = new DashboardWidget();
        w.setTab(tab);
        apply(w, dto);
        w.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() :
                widgetRepository.findByTab_IdOrderBySortOrderAsc(tabId).size());

        return widgetRepository.save(w);
    }

    @Transactional
    public DashboardWidget updateWidget(Actor actor, UUID widgetId, CreateWidgetDto dto) {
        DashboardWidget w = widgetRepository.findById(widgetId)
                .orElseThrow(() -> new NotFoundException("Widget not found"));
        assertOwner(actor, load(actor, w.getTab().getDashboard().getId()));
        apply(w, dto);
        if (dto.sortOrder() != null) w.setSortOrder(dto.sortOrder());

        return widgetRepository.save(w);
    }

    @Transactional
    public void removeWidget(Actor actor, UUID widgetId) {
        DashboardWidget w = widgetRepository.findById(widgetId)
                .orElseThrow(() -> new NotFoundException("Widget not found"));
        assertOwner(actor, load(actor, w.getTab().getDashboard().getId()));
        widgetRepository.delete(w);
    }

    private void apply(DashboardWidget w, CreateWidgetDto dto) {
        w.setLabel(dto.label().trim());
        w.setKind(dto.kind());
        w.setColor(dto.color());
        w.setIcon(dto.icon());
        w.setGridSpan(dto.gridSpan() != null ? dto.gridSpan() : 1);
        w.setQuery(dto.query());
    }

    private Dashboard load(Actor actor, UUID id) {
        Dashboard d = dashboardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dashboard not found"));
        if (d.getOwnerId() != null && !d.getOwnerId().equals(actor.id())) {
            throw new NotFoundException("Dashboard not found");
        }
        return d;
    }

    private void assertOwner(Actor actor, Dashboard d) {
        if (d.getOwnerId() == null) assertCanShare(actor);
    }

    // Toccare una dashboard condivisa è amministrazione: stessa risorsa "page" che ObjectsController
    // usa per la gestione dei tipi. La propria la gestisce chiunque, senza permessi.
    private void assertCanShare(Actor actor) {
        if (!rbacService.resolve(actor.roleIds(), "page", PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed to manage shared dashboards");
        }
    }

}
