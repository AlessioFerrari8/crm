package it.northleap.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordNote;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordNoteRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Integrazione Cal.com: riceve i webhook di prenotazione (BOOKING_CREATED/RESCHEDULED/CANCELLED)
// e li sincronizza come record `calendar_event` (il campo seminato `externalId` ospita l'uid del
// booking, quindi creazioni ripetute/riprogrammazioni fanno upsert e le cancellazioni soft-delete).
// Verifica HMAC-SHA256 sul body RAW (header X-Cal-Signature-256) — a differenza del webhook
// generico (che ri-serializza), qui la firma e' calcolata sugli stessi byte ricevuti: nessuna
// fragilita' da re-serializzazione. Parse con Jackson 2 (gia' in classpath), NON su colonne entity.
@Service
@RequiredArgsConstructor
public class CalcomWebhookService {

    private static final Logger log = LoggerFactory.getLogger(CalcomWebhookService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;
    private final RecordNoteRepository recordNoteRepository;
    private final RecordQueryService recordQueryService;

    @Value("${app.calcom.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    public Map<String, Object> receive(String signature, String rawBody) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // endpoint di fatto spento finche' non viene configurato il secret
            throw new NotFoundException("Integrazione Cal.com non configurata");
        }
        String expected = HmacUtil.sha256Hex(webhookSecret, rawBody);
        if (signature == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new RbacDeniedException("Firma webhook non valida");
        }

        Map<String, Object> event = parse(rawBody);
        String trigger = String.valueOf(event.get("triggerEvent"));
        Map<String, Object> payload = asMap(event.get("payload"));
        if (payload == null) {
            throw new BadRequestException("Payload mancante");
        }

        switch (trigger) {
            case "BOOKING_CREATED", "BOOKING_RESCHEDULED" -> upsertBooking(payload);
            case "BOOKING_CANCELLED" -> cancelBooking(payload);
            default -> {
                log.info("Webhook Cal.com ignorato: {}", trigger);
                return Map.of("ignored", true);
            }
        }
        return Map.of("ok", true);
    }

    private void upsertBooking(Map<String, Object> payload) {
        ObjectType type = calendarEventType();
        String uid = str(payload.get("uid"));
        String title = str(payload.get("title"));

        Record record = (uid != null
                ? recordQueryService.findIdByFieldValue(type.getId(), "externalId", uid)
                        .flatMap(recordRepository::findById)
                : java.util.Optional.<Record>empty())
                .orElseGet(() -> {
                    Record r = new Record();
                    r.setObjectType(type);
                    return r;
                });
        boolean isNew = record.getId() == null;

        Map<String, Object> data = new LinkedHashMap<>(record.getData() != null ? record.getData() : Map.of());
        data.put("title", title != null ? title : "Prenotazione Cal.com");
        if (payload.get("startTime") != null) data.put("start", str(payload.get("startTime")));
        if (payload.get("endTime") != null) data.put("end", str(payload.get("endTime")));
        if (payload.get("location") instanceof String loc && !loc.isBlank()) data.put("location", loc);
        if (uid != null) data.put("externalId", uid);

        record.setTitle(String.valueOf(data.get("title")));
        record.setData(data);
        recordRepository.save(record);

        // nota con i partecipanti sulla timeline dell'evento (solo alla prima creazione)
        if (isNew) {
            String attendees = attendeeSummary(payload);
            if (!attendees.isBlank()) {
                RecordNote note = new RecordNote();
                note.setRecordId(record.getId());
                note.setAuthorName("Cal.com");
                note.setBody("Prenotazione da Cal.com — partecipanti: " + attendees);
                recordNoteRepository.save(note);
            }
        }
        log.info("Cal.com booking {} sincronizzato su calendar_event {}", uid, record.getId());
    }

    private void cancelBooking(Map<String, Object> payload) {
        String uid = str(payload.get("uid"));
        if (uid == null) return;
        ObjectType type = calendarEventType();
        recordQueryService.findIdByFieldValue(type.getId(), "externalId", uid)
                .flatMap(recordRepository::findById)
                .ifPresent(r -> {
                    r.setDeleted(true);
                    recordRepository.save(r);
                    log.info("Cal.com booking {} cancellato: calendar_event {} rimosso", uid, r.getId());
                });
    }

    private ObjectType calendarEventType() {
        return objectTypeRepository.findByKey("calendar_event")
                .orElseThrow(() -> new BadRequestException("Tipo calendar_event non presente"));
    }

    private String attendeeSummary(Map<String, Object> payload) {
        if (!(payload.get("attendees") instanceof List<?> list)) return "";
        return list.stream()
                .map(CalcomWebhookService::asMap)
                .filter(a -> a != null)
                .map(a -> {
                    String name = str(a.get("name"));
                    String email = str(a.get("email"));
                    if (name != null && email != null) return name + " <" + email + ">";
                    return name != null ? name : email;
                })
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private Map<String, Object> parse(String rawBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(rawBody, Map.class);
            return parsed;
        } catch (Exception e) {
            throw new BadRequestException("Payload JSON non valido");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
