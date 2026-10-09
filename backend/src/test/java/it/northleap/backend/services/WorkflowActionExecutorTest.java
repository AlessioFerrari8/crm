package it.northleap.backend.services;

import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowActionExecutorTest {

    @Mock
    private ObjectTypeRepository objectTypeRepository;
    @Mock
    private RecordRepository recordRepository;
    @Mock
    private RecordLinkRepository recordLinkRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;

    @Mock
    private NpsService npsService;

    private WorkflowActionExecutor executor;

    @BeforeEach
    void setUp() {
        // simula la generazione UUID in-memory di Hibernate (GenerationType.UUID, popolata
        // subito al save senza bisogno di round-trip col DB) - il mock di RecordRepository non
        // lo fa da solo
        lenient().when(recordRepository.save(any(Record.class))).thenAnswer(inv -> {
            Record r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        executor = new WorkflowActionExecutor(
                objectTypeRepository, recordRepository, recordLinkRepository,
                new RecordValidator(), new ConditionEvaluator(), JsonMapper.builder().build(),
                emailService, notificationService, npsService);
    }

    private ObjectType objectType(String key, FieldDef... fields) {
        ObjectType obj = new ObjectType();
        obj.setId(UUID.randomUUID());
        obj.setKey(key);
        obj.setFields(List.of(fields));
        return obj;
    }

    private FieldDef field(String key, FieldType type) {
        FieldDef f = new FieldDef();
        f.setKey(key);
        f.setType(type);
        return f;
    }

    private Record record(Map<String, Object> data) {
        Record r = new Record();
        r.setId(UUID.randomUUID());
        r.setData(data);
        return r;
    }

    @Test
    void updateRecordSkipsWhenRecordNull() {
        Map<String, Object> result = executor.execute(Map.of("type", "update_record", "set", Map.of("stage", "won")), null);
        assertEquals(true, result.get("skipped"));
    }

    @Test
    void updateRecordMergesDataAndStatusColumn() {
        Record r = record(new java.util.LinkedHashMap<>(Map.of("stage", "prospect")));
        Map<String, Object> action = Map.of("type", "update_record", "set", Map.of("status", "won"));
        executor.execute(action, r);
        verify(recordRepository).save(r);
        assertEquals("won", r.getStatus());
        assertEquals("won", r.getData().get("status"));
        assertEquals("prospect", r.getData().get("stage"));
    }

    @Test
    void createRecordInterpolatesMapAndDerivesTitleStatus() {
        FieldDef nameField = field("name", FieldType.TEXT);
        ObjectType opp = objectType("opportunity", nameField);
        when(objectTypeRepository.findByKey("opportunity")).thenReturn(Optional.of(opp));
        Record source = record(Map.of("name", "Acme"));

        Map<String, Object> action = Map.of("type", "create_record", "objectKey", "opportunity",
                "map", Map.of("name", "Opp. da {{record.name}}"));
        Map<String, Object> result = executor.execute(action, source);

        assertNotNull(result.get("created"));
        verify(recordRepository).save(any(Record.class));
    }

    @Test
    void createRecordReturnsErrorForUnknownObjectKey() {
        when(objectTypeRepository.findByKey("nope")).thenReturn(Optional.empty());
        Map<String, Object> result = executor.execute(Map.of("type", "create_record", "objectKey", "nope", "map", Map.of()), null);
        assertEquals("object inesistente", result.get("error"));
    }

    @Test
    void createLinkSkipsWhenNoTargetId() {
        Map<String, Object> result = executor.execute(Map.of("type", "create_link"), record(Map.of()));
        assertEquals(true, result.get("skipped"));
    }

    @Test
    void createLinkCreatesRecordLink() {
        UUID targetId = UUID.randomUUID();
        Record target = record(Map.of());
        when(recordRepository.getReferenceById(targetId)).thenReturn(target);
        Map<String, Object> action = Map.of("type", "create_link", "_targetId", targetId.toString(), "relationKey", "contact.company");
        Map<String, Object> result = executor.execute(action, record(Map.of()));
        assertEquals(true, result.get("linked"));
        verify(recordLinkRepository).save(any());
    }

    @Test
    void createReminderUsesDueFieldWithOffsetDays() {
        ObjectType reminderType = objectType("reminder");
        when(objectTypeRepository.findByKey("reminder")).thenReturn(Optional.of(reminderType));
        Instant deadline = Instant.now().plus(10, ChronoUnit.DAYS);
        Record source = record(Map.of("deadline", deadline.toString()));

        Map<String, Object> action = Map.of("type", "create_reminder", "title", "Scadenza {{record.deadline}}",
                "dueField", "deadline", "offsetDays", 3, "_objectKey", "project");
        executor.execute(action, source);

        org.mockito.ArgumentCaptor<Record> captor = org.mockito.ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(captor.capture());
        Map<String, Object> data = captor.getValue().getData();
        Instant dueAt = Instant.parse((String) data.get("dueAt"));
        assertTrue(dueAt.isBefore(deadline));
        assertEquals(false, data.get("done"));
    }

    @Test
    void createTaskFallsBackToDelayHoursWhenNoDueField() {
        ObjectType taskType = objectType("task");
        when(objectTypeRepository.findByKey("task")).thenReturn(Optional.of(taskType));
        Record source = record(Map.of());

        Map<String, Object> action = Map.of("type", "create_task", "title", "Analisi", "delayHours", 48);
        executor.execute(action, source);

        org.mockito.ArgumentCaptor<Record> captor = org.mockito.ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(captor.capture());
        assertEquals("todo", captor.getValue().getStatus());
        Instant dueDate = Instant.parse((String) captor.getValue().getData().get("dueDate"));
        assertTrue(dueDate.isAfter(Instant.now().plus(47, ChronoUnit.HOURS)));
    }

    @Test
    void createCalendarEventComputesStartAndEnd() {
        ObjectType calType = objectType("calendar_event");
        when(objectTypeRepository.findByKey("calendar_event")).thenReturn(Optional.of(calType));
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);
        Record source = record(Map.of("dueDate", due.toString()));

        Map<String, Object> action = Map.of("type", "create_calendar_event", "title", "Evento", "startField", "dueDate");
        executor.execute(action, source);

        org.mockito.ArgumentCaptor<Record> captor = org.mockito.ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(captor.capture());
        Map<String, Object> data = captor.getValue().getData();
        Instant start = Instant.parse((String) data.get("start"));
        Instant end = Instant.parse((String) data.get("end"));
        assertEquals(due, start);
        assertEquals(start.plus(1, ChronoUnit.HOURS), end);
    }

    @Test
    void notifyUserSkipsWhenNoUserId() {
        Map<String, Object> result = executor.execute(Map.of("type", "notify_user"), record(Map.of()));
        assertEquals(true, result.get("skipped"));
    }

    // il salvataggio e la pubblicazione di NotifyEvent stanno dentro NotificationService.create():
    // qui si verifica solo che l'azione risolva il destinatario e deleghi con i valori giusti
    @Test
    void notifyUserResolvesOwnerTargetAndDelegatesToNotificationService() {
        UUID ownerId = UUID.randomUUID();
        Record r = record(Map.of());
        r.setOwnerId(ownerId);
        Map<String, Object> action = Map.of("type", "notify_user", "target", "owner", "title", "Ciao");
        Map<String, Object> result = executor.execute(action, r);
        assertEquals(ownerId, result.get("notified"));
        verify(notificationService).create(eq(ownerId), eq("Ciao"), isNull(), isNull());
    }

    @Test
    void sendWebhookSkippedWhenUrlMissing() {
        Map<String, Object> result = executor.execute(Map.of("type", "send_webhook"), record(Map.of()));
        assertEquals(true, result.get("skipped"));
    }

    @Test
    void sendWebhookBlockedBySsrfGuard() {
        Map<String, Object> action = Map.of("type", "send_webhook", "url", "http://169.254.169.254/latest/meta-data");
        Map<String, Object> result = executor.execute(action, record(Map.of()));
        assertEquals("url non consentito (SSRF)", result.get("skipped"));
    }

    @Test
    void sendEmailSkippedWhenNotConfigured() {
        Map<String, Object> action = Map.of("type", "send_email", "to", "a@b.com");
        Map<String, Object> result = executor.execute(action, record(Map.of()));
        assertEquals("no key/to", result.get("skipped"));
    }

    @Test
    void delayActionDoesNotSleepAndReturnsMs() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = executor.execute(Map.of("type", "delay", "ms", 5000), null);
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(5000, result.get("delayed"));
        assertTrue(elapsed < 1000, "delay action non deve dormire davvero (vestigiale, fedele all'originale)");
    }

    @Test
    void unknownActionTypeReturnsUnknown() {
        Map<String, Object> result = executor.execute(Map.of("type", "frobnicate"), null);
        assertEquals("frobnicate", result.get("unknown"));
    }

    @Test
    void sendEmailDelegatesToEmailService() {
        when(emailService.isConfigured()).thenReturn(true);
        when(emailService.send(isNull(), eq("a@b.com"), eq("Oggetto"), eq("<p>Hi!</p>"))).thenReturn(true);

        Map<String, Object> action = Map.of(
                "type", "send_email", "to", "a@b.com", "subject", "Oggetto", "html", "<p>Hi!</p>"
        );

        Map<String, Object> result = executor.execute(action, record(Map.of()));

        assertEquals(Boolean.TRUE, result.get("email"));
        verify(emailService).send(isNull(), eq("a@b.com"), eq("Oggetto"), eq("<p>Hi!</p>"));
    }
}
