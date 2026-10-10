package it.northleap.backend.services;

import it.northleap.backend.dtos.FilterGroup;
import it.northleap.backend.dtos.QueryRecordsDto;
import it.northleap.backend.dtos.RecordQueryResponse;
import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

// i tool mcp: generici su objectKey, non uno per modulo (con 34 moduli sarebbero 170 tool, nessun modello li usa bene)
// Tutto passa da RecordsService: RBAC, scope e audit sono quelli del CRM, con l'Actor della API key. Nessun tool di
// cancellazione, nessun tool che accetti SQL, path o URL
@Service
@RequiredArgsConstructor
public class McpTools {

    private static final int MAX_LIMIT = 50;

    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;
    private final RecordsService recordsService;
    private final ReportService reportService;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;

    // le descrizioni sono l'interfaccia utente di questa fase: è da qui che il modello capisce cosa fare
    public List<Map<String, Object>> definitions() {
        return List.of(
                tool("list_objects", "Elenca i tipi di record del CRM visibili con questa chiave (chiave e nome). Parti da qui.",
                        Map.of()),
                tool("describe_object", "Descrive i campi di un tipo di record: chiave, etichetta, tipo, obbligatorieta', valori ammessi. "
                                + "Usalo SEMPRE prima di search_records, create_record o update_record: i nomi dei campi non si indovinano.",
                        Map.of("objectKey", prop("string", "chiave del tipo, da list_objects"))),
                tool("search_records", "Cerca record di un tipo. `query` e' testo libero sul titolo; `filter` e' un gruppo "
                                + "{combinator:'and'|'or', conditions:[{field, op, value}]} con op fra eq, neq, contains, gt, gte, lt, lte, "
                                + "is_empty, is_not_empty. Restituisce id, titolo, stato e i campi chiesti in `fields`. Massimo 50 risultati.",
                        Map.of("objectKey", prop("string", "chiave del tipo"),
                                "query", prop("string", "testo da cercare nel titolo (facoltativo)"),
                                "filter", Map.of("type", "object", "description", "filtro strutturato (facoltativo)"),
                                "fields", Map.of("type", "array", "items", Map.of("type", "string"), "description", "campi da includere nel risultato"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT))),
                tool("get_record", "Legge un record completo dato il suo id.",
                        Map.of("recordId", prop("string", "UUID del record"))),
                tool("create_record", "Crea un record. `data` usa le chiavi di describe_object; i campi obbligatori vanno tutti.",
                        Map.of("objectKey", prop("string", "chiave del tipo"), "data", Map.of("type", "object"))),
                tool("update_record", "Modifica un record: in `data` solo i campi da cambiare, gli altri restano come sono.",
                        Map.of("recordId", prop("string", "UUID del record"), "data", Map.of("type", "object"))),
                tool("run_report", "Esegue un report salvato del CRM e ne restituisce i numeri.",
                        Map.of("reportId", prop("string", "UUID del report")))
        );
    }

    public Object call(Actor actor, String name, Map<String, Object> args) {
        return switch (name) {
            case "list_objects" -> objectTypeRepository.findAllByOrderBySortOrderAsc().stream()
                    .filter(t -> can(actor, t.getKey()))
                    .map(t -> Map.of("key", t.getKey(), "label", t.getLabel()))
                    .toList();
            case "describe_object" -> describe(actor, str(args, "objectKey"));
            case "search_records" -> search(actor, args);
            case "get_record" -> {
                Record r = recordsService.findOne(actor, keyOf(uuid(args, "recordId")), uuid(args, "recordId")).record();
                yield full(r);
            }
            case "create_record" -> full(recordsService.create(actor, str(args, "objectKey"), dto(args), "mcp"));
            case "update_record" -> {
                UUID id = uuid(args, "recordId");
                yield full(recordsService.update(actor, keyOf(id), id, dto(args), "mcp"));
            }
            case "run_report" -> reportService.run(actor, uuid(args, "reportId"));
            default -> throw new BadRequestException("Tool sconosciuto: " + name);
        };
    }

    private Object describe(Actor actor, String key) {
        if (!can(actor, key)) throw new RbacDeniedException("Tipo non visibile con questa chiave");
        ObjectType t = objectTypeRepository.findByKey(key).orElseThrow(() -> new NotFoundException("Tipo sconosciuto: " + key));
        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldDef f : t.getFields()) {
            if (f.isHidden()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", f.getKey());
            m.put("label", f.getLabel());
            m.put("type", f.getType().name());
            m.put("required", f.isRequired());
            if (f.getOptions() != null && !f.getOptions().isEmpty()) m.put("options", f.getOptions());
            fields.add(m);
        }
        return Map.of("key", t.getKey(), "label", t.getLabel(), "fields", fields);
    }

    @SuppressWarnings("unchecked")
    private Object search(Actor actor, Map<String, Object> args) {
        QueryRecordsDto q = new QueryRecordsDto();
        q.setQ(str(args, "query"));
        if (args.get("filter") instanceof Map<?, ?> f) q.setFilter(objectMapper.convertValue(f, FilterGroup.class));
        int limit = args.get("limit") instanceof Number n ? n.intValue() : 20;
        q.setPageSize(Math.max(1, Math.min(limit, MAX_LIMIT)));
        List<String> wanted = args.get("fields") instanceof List<?> l ? (List<String>) l : List.of();
        RecordQueryResponse res = recordsService.query(actor, str(args, "objectKey"), q);
        // risultati SNELLI: il jsonb intero di 50 record riempie il contesto del modello per niente
        List<Map<String, Object>> items = res.items().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("status", r.getStatus());
            for (String k : wanted) if (r.getData() != null && r.getData().containsKey(k)) m.put(k, r.getData().get(k));
            return m;
        }).toList();
        return Map.of("total", res.total(), "items", items);
    }

    private Map<String, Object> full(Record r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("objectKey", r.getObjectType().getKey());
        m.put("title", r.getTitle());
        m.put("status", r.getStatus());
        m.put("data", r.getData());
        return m;
    }

    @SuppressWarnings("unchecked")
    private UpsertRecordDto dto(Map<String, Object> args) {
        if (!(args.get("data") instanceof Map<?, ?> data)) throw new BadRequestException("`data` mancante");
        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(new LinkedHashMap<>((Map<String, Object>) data));
        return dto;
    }

    private String keyOf(UUID id) {
        return recordRepository.findByIdAndIsDeletedFalse(id)
                .map(r -> r.getObjectType().getKey())
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
    }

    private boolean can(Actor actor, String key) {
        return rbacService.resolve(actor.roleIds(), key, PermAction.READ).allowed();
    }

    private static String str(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> a, String k) {
        try {
            return UUID.fromString(String.valueOf(a.get(k)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("`" + k + "` non e' un UUID valido");
        }
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> props) {
        List<String> required = props.keySet().stream()
                .filter(k -> !List.of("query", "filter", "fields", "limit").contains(k)).sorted().toList();
        return Map.of("name", name, "description", description,
                "inputSchema", Map.of("type", "object", "properties", props, "required", required));
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }
}
