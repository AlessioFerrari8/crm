package it.northleap.backend.services;

import it.northleap.backend.dtos.PublicFormResponse;
import it.northleap.backend.dtos.PublicFormSubmission;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.events.RecordCreatedEvent;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Web form pubblico di lead capture (alla LeadBooster di Pipedrive): un tipo con publicForm=true
// espone GET (definizione campi) e POST (submission) senza autenticazione. Le difese: opt-in
// esplicito per tipo, whitelist dei campi compilabili, honeypot anti-bot, rate limit per IP
// (RateLimitFilter), e la validazione completa del RecordValidator su ogni submission.
// La creazione pubblica il RecordCreatedEvent: i workflow "nuovo lead" scattano normalmente.
@Service
@RequiredArgsConstructor
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);

    // tipi campo esclusi dal modulo pubblico: calcolati, relazionali o troppo "interni"
    private static final Set<FieldType> EXCLUDED_TYPES = Set.of(
            FieldType.AUTONUMBER, FieldType.FORMULA, FieldType.ROLLUP,
            FieldType.RELATION, FieldType.LOOKUP, FieldType.USER,
            FieldType.FILE, FieldType.IMAGE, FieldType.JSON
    );

    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;
    private final RecordValidator recordValidator;
    private final ApplicationEventPublisher events;

    public PublicFormResponse definition(String key) {
        ObjectType type = publicTypeOrThrow(key);
        return new PublicFormResponse(type.getKey(), type.getLabel(), type.getColor(), allowedFields(type));
    }

    @Transactional
    public Map<String, Object> submit(String key, PublicFormSubmission submission) {
        ObjectType type = publicTypeOrThrow(key);

        // honeypot compilato = bot: finto successo, nessun record
        if (submission.getWebsite() != null && !submission.getWebsite().isBlank()) {
            log.info("Submission form pubblico '{}' scartata (honeypot)", key);
            return Map.of("ok", true);
        }

        // accetta solo i campi effettivamente esposti dal modulo
        Set<String> allowedKeys = allowedFields(type).stream().map(FieldDef::getKey).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> filtered = new LinkedHashMap<>();
        submission.getData().forEach((k, v) -> {
            if (allowedKeys.contains(k)) filtered.put(k, v);
        });

        Map<String, Object> data = recordValidator.validate(type.getFields(), filtered);

        Record record = new Record();
        record.setObjectType(type);
        record.setTitle(recordValidator.deriveTitle(type.getFields(), data));
        record.setStatus(recordValidator.deriveStatus(type.getFields(), data));
        record.setData(data);
        recordRepository.save(record);

        // i workflow (es. "Benvenuto nuovo lead") devono scattare come per una creazione normale
        events.publishEvent(new RecordCreatedEvent(key, record));
        log.info("Form pubblico '{}': creato record {}", key, record.getId());
        return Map.of("ok", true);
    }

    private ObjectType publicTypeOrThrow(String key) {
        // 404 identico per "non esiste" e "form non abilitato": non riveliamo quali tipi esistono
        return objectTypeRepository.findByKey(key)
                .filter(t -> t.isEnabled() && t.isPublicForm())
                .orElseThrow(() -> new NotFoundException("Modulo non disponibile"));
    }

    private List<FieldDef> allowedFields(ObjectType type) {
        return type.getFields().stream()
                .filter(f -> !f.isHidden() && !f.isReadonly() && !EXCLUDED_TYPES.contains(f.getType()))
                .sorted(java.util.Comparator.comparingInt(FieldDef::getSortOrder))
                .toList();
    }
}
