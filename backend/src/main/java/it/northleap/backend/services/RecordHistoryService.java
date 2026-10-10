package it.northleap.backend.services;

import it.northleap.backend.dtos.HistoryEntry;
import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.AuditLogRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.SuppressAjWarnings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;


// la cronologia dentro la scheda, con gli stessi permessi della scheda (findOne), non quelli di /api/logs
@Service
@RequiredArgsConstructor
public class RecordHistoryService {

    private static final Map<String, String> ACTIONS = Map.of(
            "create", "Creato", "update", "Modificato", "delete", "Eliminato", "restore", "Ripristinato",
            "bulk_update", "Modifica di massa", "einvoice.generate", "Fattura elettronica generata",
            "einvoice.sent", "Fattura elettronica inviata", "quote.link", "Link di accettazione inviato"
    );

    private final RecordsService recordsService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final RelationTitleService relationTitleService;
    private final UserPreferenceService userPreferenceService;
    private final MoneyFormatter fmt;

    public HistoryEntry.Page history(Actor actor, String key, UUID id, int page, int size) {
        Record record = recordsService.findOne(actor, key, id).record();
        Map<String, FieldDef> fields = new HashMap<>();
        record.getObjectType().getFields().forEach(f -> fields.put(f.getKey(), f));
        UserPreference pref = userPreferenceService.get(actor);

        Page<AuditLog> logs = auditLogRepository.findByResourceAndResourceIdOrderByCreatedAtDesc(
                key, id.toString(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        // gli UUID delle relation diventano titoli, tutti in una query per pagina
        Set<UUID> ids = new HashSet<>();
        logs.forEach(l -> diffSides(l).forEach(side -> side.forEach((k, v) -> {
            FieldDef f = fields.get(k);
            if (f != null && f.getType() == FieldType.RELATION) relationTitleService.collectIds(v, ids);
        })));
        Map<String, String> titles = relationTitleService.titlesOf(ids);
        Map<UUID, String> authors = new HashMap<>();

        return new HistoryEntry.Page(logs.map(l -> new HistoryEntry(
                ACTIONS.getOrDefault(l.getAction(), l.getAction()),
                author(l, authors),
                l.getCreatedAt(),
                changes(l, fields, titles, pref))).getContent(), logs.getNumber(), logs.getTotalPages());
    }

    private List<HistoryEntry.Change> changes(AuditLog l, Map<String, FieldDef> fields, Map<String, String> titles, UserPreference pref) {
        List<Map<String, Object>> sides = diffSides(l);
        if (sides.size() < 2) return List.of();
        Map<String, Object> before = sides.get(0), after = sides.get(1);
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<HistoryEntry.Change> out = new ArrayList<>();
        for (String k : keys) {
            if (Objects.equals(before.get(k), after.get(k))) continue;
            FieldDef f = fields.get(k);
            // campi tecnici senza FieldDef (acceptToken...) non sono storia per chi legge la scheda
            if (f == null) continue;
            out.add(new HistoryEntry.Change(f.getLabel(), format(f, before.get(k), titles, pref), format(f, after.get(k),
                    titles, pref)));
        }
        return out;
    }

    private String format(FieldDef f, Object v, Map<String, String> titles, UserPreference pref) {
        if (v == null || v.toString().isBlank()) return "-";
        try {
            return switch (f.getType()) {
                case CURRENCY -> fmt.money(new BigDecimal(v.toString()), pref);
                case BOOLEAN -> Boolean.parseBoolean(v.toString()) ? "Sì" : "No";
                case RELATION -> {
                    Set<UUID> ids = new LinkedHashSet<>();
                    relationTitleService.collectIds(v, ids);
                    yield String.join(", ", ids.stream().map(id -> titles.getOrDefault(id.toString(), "?")).toList());
                }
                default -> v.toString();
            };
        } catch (NumberFormatException e) {
            return v.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> diffSides(AuditLog l) {
        if (l.getDiff() == null) return List.of();
        Object b = l.getDiff().get("before"), a = l.getDiff().get("after");
        if (!(b instanceof Map<?, ?>) || !(a instanceof Map<?, ?>)) return List.of();
        return List.of((Map<String, Object>) b, (Map<String, Object>) a);
    }

    private String author(AuditLog l, Map<UUID, String> cache) {
        if (l.getActorId() == null) return "Sistema";
        if ("apikey".equalsIgnoreCase(l.getActorType())) return "API key";
        return cache.computeIfAbsent(l.getActorId(), id -> userRepository.findById(id)
                .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail()).orElse("-"));
    }


}
