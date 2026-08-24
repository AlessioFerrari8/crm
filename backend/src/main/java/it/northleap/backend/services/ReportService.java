package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateReportDto;
import it.northleap.backend.dtos.ReportQuery;
import it.northleap.backend.dtos.ReportResult;
import it.northleap.backend.entities.Report;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.ReportRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Crud dei report + esecuzione. L'aggregazione la fa ReportEngine: qui dentro non c'è una sola riga di SQL
@Service
@RequiredArgsConstructor
public class ReportService {

    // vari inject
    private final ReportRepository reportRepository;
    private final ReportEngine reportEngine;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;

    public List<Report> list(Actor actor) {
        return reportRepository.findByOwnerIdIsNullOrOwnerIdOrderByFolderAscTitleAsc(actor.id());
    }

    @Transactional
    public Report create(Actor actor, CreateReportDto dto) {
        assertCanRead(actor, dto.objectKey());
        Report report = new Report();
        apply(report, dto, actor);
        return reportRepository.save(report);
    }

    @Transactional
    public Report update(Actor actor, UUID id, CreateReportDto createReportDto) {
        Report report = load(actor, id);
        assertOwner(actor, report);
        assertCanRead(actor, createReportDto.objectKey());
        apply(report, createReportDto, actor);
        return reportRepository.save(report);
    }

    @Transactional
    public void remove(Actor actor, UUID id) {
        Report report = load(actor, id);
        assertOwner(actor, report);
        reportRepository.delete(report);
    }

    // duplica: negli screen è l'azione più usata della lista report, perché un report nuovo nasce quasi sempre da uno che c'è già
    // La copia è sempre personale, anche se l'originale è condiviso, chi duplica sta sperimentando, non pubblicando
    @Transactional
    public Report duplicate(Actor actor, UUID id) {
        Report source = load(actor, id);
        Report copy = new Report();
        copy.setTitle(source.getTitle() + " (copia)");
        copy.setRender(source.getRender());
        copy.setObjectKey(source.getObjectKey());
        copy.setFolder(source.getFolder());
        copy.setOwnerId(actor.id());
        copy.setQuery(source.getQuery());
        return reportRepository.save(copy);
    }

    // esecuzione di un report salvato. Il permesso si controlla qui e non nel motore: il motore è
    // una funzione pura da query a risultato, e viene chiamato anche dai widget, che hanno il loro controllo.
    public ReportResult run(Actor actor, UUID id) {
        Report report = load(actor, id);
        assertCanRead(actor, report.getObjectKey());
        return reportEngine.run(toQuery(report.getQuery()));
    }

    // anteprima del wizard, una query al volo, senza salvare niente. È quello che rende il costruttore di report
    // usabile; si vede il risultato mentre lo si compone
    public ReportResult preview(Actor actor, Map<String, Object> rawQuery) {
        ReportQuery query = toQuery(rawQuery);
        assertCanRead(actor, query.objectKey());
        return reportEngine.run(query);
    }

    // export/import, il JSON della definizione. Serve a portarsi i report da un'installazione
    // all'altra
    public Map<String, Object> export(Actor actor, UUID id) {
        Report r = load(actor, id);
        return Map.of("title", r.getTitle(), "render", r.getRender().name(),
        "objectKey", r.getObjectKey(), "folder", r.getFolder() != null ? r.getFolder() : "",
        "query", r.getQuery());
    }

    @Transactional
    public Report importDefinition(Actor actor, Map<String, Object> raw) {
        CreateReportDto dto = new CreateReportDto(
                String.valueOf(raw.getOrDefault("title", "Report importato")),
                Report.ReportRender.valueOf(String.valueOf(raw.getOrDefault("render", "GRID"))),
                String.valueOf(raw.get("objectKey")),
                raw.get("folder") != null ? String.valueOf(raw.get("folder")) : null,
                true, // un import nasce personale
                castQuery(raw.get("query")));
        return create(actor, dto);
    }

    private void apply(Report report, CreateReportDto dto, Actor actor) {
        report.setTitle(dto.title().trim());
        report.setRender(dto.render());
        report.setObjectKey(dto.objectKey());
        report.setFolder(dto.folder() != null && !dto.folder().isBlank() ? dto.folder().trim() : null);
        report.setOwnerId(dto.personal() ? actor.id() : null);
        report.setQuery(dto.query());
    }

    private Report load(Actor actor, UUID id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        // un report personale di un altro non esiste, per chi lo chiede
        if (report.getOwnerId() != null && !report.getOwnerId().equals(actor.id())) {
            throw new NotFoundException("Report not found");
        }
        return report;
    }

    // modificare / cancellare un report condiviso è un azione da chi gestisce i grafici
    // stessa risorsa RBAC di ChartController, nessun permesso nuovo da seminare. Il proprio
    // se lo gestisce chiunque
    private void assertOwner(Actor actor, Report report) {
        if (report.getOwnerId() == null && !rbacService.resolve(actor.roleIds(), "chart", PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed to manage shared reports");
        }
    }

    // il report legge i record di un modulo: chi non può leggere quel modulo non può leggerne nemmeno
    // il totale aggregato
    // Senza questo controllo un report è un modo elegante per esfiltrare dati che l'RBAC nega
    private void assertCanRead(Actor actor, String objectKey) {
        if (!rbacService.resolve(actor.roleIds(), objectKey, PermAction.READ).allowed()) {
            throw new RbacDeniedException("Not allowed on " + objectKey);
        }
    }


    // da Map (jsonb) a ReportQuery: la conversione la fa Jackson, così gli enum e i record annidati
    // dimensione e metric, arrivano tipizzati e un valore sbagliato esce come errore di conversione e
    // non come NPE 3 metodi dopo
    private ReportQuery toQuery(Map<String, Object> raw) {
        return objectMapper.convertValue(raw, ReportQuery.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castQuery(Object raw) {
        return raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}





