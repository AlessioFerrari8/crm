package it.northleap.backend.services;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Porting di WorkflowEngine.execute() (workflow.engine.ts): un'azione di workflow alla volta,
// invocata sia dall'esecutore lineare (WorkflowEngine) sia dal GraphWorkflowRunner per i nodi
// "action". Niente RecordValidator.validate() sui record creati da workflow (l'originale non
// valida, fedelmente non lo facciamo nemmeno qui); niente RecordsService per i create (i record
// creati da un'azione NON pubblicano RecordCreatedEvent e non vengono audit-loggati, esattamente
// come l'originale che scrive via Prisma diretto invece che tramite il service applicativo - un
// workflow non fa scatenare a cascata altri workflow sullo stesso evento, limite consapevole
// dell'originale, non un bug del porting).
@Service
@RequiredArgsConstructor
public class WorkflowActionExecutor {

    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;
    private final RecordLinkRepository recordLinkRepository;
    private final RecordValidator recordValidator;
    private final ConditionEvaluator conditionEvaluator;
    // niente ApplicationEventPublisher qui: NotifyEvent lo pubblica NotificationService.create()
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Transactional
    public Map<String, Object> execute(Map<String, Object> action, Record record) {
        String type = String.valueOf(action.get("type"));
        return switch (type) {
            case "update_record" -> updateRecord(action, record);
            case "create_record" -> createRecord(action, record);
            case "create_link" -> createLink(action, record);
            case "create_task" -> createTaskOrReminder("task", action, record);
            case "create_reminder" -> createTaskOrReminder("reminder", action, record);
            case "create_calendar_event" -> createCalendarEvent(action, record);
            case "notify_user" -> notifyUser(action, record);
            case "send_email" -> sendEmail(action, record);
            case "send_webhook", "call_api" -> sendWebhook(action, record);
            // l'originale non dorme qui: il vero sleep accade solo nei nodi "delay" del grafo
            // (graph-runner.ts), mai in questo switch lineare - questo case e' vestigiale, non un bug
            case "delay" -> Map.of("delayed", action.getOrDefault("ms", 0));
            default -> Map.of("unknown", type);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateRecord(Map<String, Object> action, Record record) {
        if (record == null) {
            return Map.of("skipped", true);
        }
        Map<String, Object> set = (Map<String, Object>) action.getOrDefault("set", Map.of());
        Map<String, Object> data = new LinkedHashMap<>(record.getData() != null ? record.getData() : Map.of());
        // nota fedeltà: se `set` contiene "status", finisce sia nella colonna status SIA dentro
        // data (replica esatta del comportamento originale, non corretto qui)
        data.putAll(set);
        record.setData(data);
        if (set.get("status") != null) {
            record.setStatus(String.valueOf(set.get("status")));
        }
        recordRepository.save(record);
        return Map.of("updated", record.getId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createRecord(Map<String, Object> action, Record record) {
        ObjectType obj = objectTypeRepository.findByKey(String.valueOf(action.get("objectKey"))).orElse(null);
        if (obj == null) {
            return Map.of("error", "object inesistente");
        }
        Map<String, Object> map = (Map<String, Object>) action.getOrDefault("map", Map.of());
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            data.put(e.getKey(), e.getValue() instanceof String tpl ? conditionEvaluator.interpolate(tpl, record) : e.getValue());
        }
        Record created = new Record();
        created.setObjectType(obj);
        created.setTitle(recordValidator.deriveTitle(obj.getFields(), data));
        created.setStatus(recordValidator.deriveStatus(obj.getFields(), data));
        created.setData(data);
        recordRepository.save(created);
        return Map.of("created", created.getId());
    }

    private Map<String, Object> createLink(Map<String, Object> action, Record record) {
        Object targetId = action.get("_targetId");
        if (record == null || targetId == null) {
            return Map.of("skipped", true);
        }
        RecordLink link = new RecordLink();
        link.setSource(record);
        link.setTarget(recordRepository.getReferenceById(UUID.fromString(String.valueOf(targetId))));
        link.setRelationKey(String.valueOf(action.get("relationKey")));
        recordLinkRepository.save(link);
        return Map.of("linked", true);
    }

    private Map<String, Object> createTaskOrReminder(String key, Map<String, Object> action, Record record) {
        ObjectType obj = objectTypeRepository.findByKey(key).orElse(null);
        if (obj == null) {
            return Map.of("error", "object inesistente");
        }
        String title = conditionEvaluator.interpolate(String.valueOf(action.getOrDefault("title", "Nuovo")), record);

        Object dueFieldName = action.get("dueField");
        Object fromField = dueFieldName != null && record != null && record.getData() != null
                ? record.getData().get(String.valueOf(dueFieldName)) : null;
        Instant due;
        if (fromField != null) {
            due = Instant.parse(String.valueOf(fromField));
            Object offsetDays = action.get("offsetDays");
            if (offsetDays != null) {
                due = due.minus(((Number) offsetDays).longValue(), ChronoUnit.DAYS);
            }
        } else {
            long delayHours = action.get("delayHours") != null ? ((Number) action.get("delayHours")).longValue() : 24;
            due = Instant.now().plus(delayHours, ChronoUnit.HOURS);
        }
        String dueAt = due.toString();

        Map<String, Object> data;
        String status = null;
        if ("reminder".equals(key)) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("title", title);
            d.put("dueAt", dueAt);
            d.put("done", false);
            d.put("sourceId", record != null ? record.getId() : null);
            d.put("sourceObject", action.get("_objectKey"));
            data = d;
        } else {
            status = "todo";
            data = Map.of("title", title, "status", "todo", "dueDate", dueAt);
        }

        Record created = new Record();
        created.setObjectType(obj);
        created.setTitle(title);
        created.setStatus(status);
        created.setData(data);
        recordRepository.save(created);
        return Map.of("created", created.getId());
    }

    private Map<String, Object> createCalendarEvent(Map<String, Object> action, Record record) {
        ObjectType obj = objectTypeRepository.findByKey("calendar_event").orElse(null);
        if (obj == null) {
            return Map.of("error", "object inesistente");
        }
        String title = conditionEvaluator.interpolate(String.valueOf(action.getOrDefault("title", "Evento")), record);
        Object startFieldName = action.get("startField");
        Object fromField = startFieldName != null && record != null && record.getData() != null
                ? record.getData().get(String.valueOf(startFieldName)) : null;
        Instant start = fromField != null ? Instant.parse(String.valueOf(fromField)) : Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        Record created = new Record();
        created.setObjectType(obj);
        created.setTitle(title);
        created.setData(Map.of("title", title, "start", start.toString(), "end", end.toString()));
        recordRepository.save(created);
        return Map.of("created", created.getId());
    }

    private Map<String, Object> notifyUser(Map<String, Object> action, Record record) {
        UUID userId = "owner".equals(action.get("target"))
                ? (record != null ? record.getOwnerId() : null)
                : (action.get("userId") != null ? UUID.fromString(String.valueOf(action.get("userId"))) : null);
        if (userId == null) {
            return Map.of("skipped", true);
        }
        notificationService.create(
                userId, conditionEvaluator.interpolate(String.valueOf(action.getOrDefault("title", "Notifica")), record),
                action.get("body") != null
                        ? conditionEvaluator.interpolate(String.valueOf(action.get("body")), record) : null,
                action.get("link") != null ? String.valueOf(action.get("link")) : null);
        return Map.of("notified", userId);
    }

    private Map<String, Object> sendEmail(Map<String, Object> action, Record record) {
        String to = action.get("to") != null ? conditionEvaluator.interpolate(String.valueOf(action.get("to")), record) : "";
        if (to.isBlank() || !emailService.isConfigured()) {
            return Map.of("skipped", "no key/to");
        }
        String subject = conditionEvaluator.interpolate(String.valueOf(action.getOrDefault("subject", "NorthLeap CRM")), record);
        String html = action.get("html") != null
                ? conditionEvaluator.interpolate(String.valueOf(action.get("html")), record)
                : "<p>Template: " + action.getOrDefault("template", "default") + "</p>";
        String from = action.get("from") != null ? String.valueOf(action.get("from")) : null;

        boolean sent = emailService.send(from, to, subject, html);
        return Map.of("email", sent);
    }

    private Map<String, Object> sendWebhook(Map<String, Object> action, Record record) {
        Object url = action.get("url");
        if (url == null) {
            return Map.of("skipped", true);
        }
        String urlStr = String.valueOf(url);
        if (!SafeUrlValidator.isSafe(urlStr)) {
            return Map.of("skipped", "url non consentito (SSRF)");
        }
        Map<String, Object> payload = Map.of("event", action.getOrDefault("event", "workflow"), "record", record);
        String bodyJson = objectMapper.writeValueAsString(payload);
        String method = action.get("method") != null ? String.valueOf(action.get("method")) : "POST";

        RestClient.RequestBodySpec req = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(urlStr)
                .contentType(MediaType.APPLICATION_JSON);
        Object secret = action.get("secret");
        if (secret != null) {
            req = req.header("X-Signature", HmacUtil.sha256Hex(String.valueOf(secret), bodyJson));
        }
        int status = req.body(bodyJson).exchange((rq, resp) -> resp.getStatusCode().value());
        return Map.of("status", status);
    }
}
