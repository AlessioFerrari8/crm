package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateObjectTypeDto;
import it.northleap.backend.dtos.FieldDefDto;
import it.northleap.backend.dtos.UpdateObjectTypeDto;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.FieldDefRepository;
import it.northleap.backend.repositories.ObjectTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Porting di objects.service.ts: CRUD su ObjectType + FieldDef, più semplice del motore Record.
@Service
@RequiredArgsConstructor
public class ObjectsService {

    private final ObjectTypeRepository objectTypeRepository;
    private final FieldDefRepository fieldDefRepository;
    private final PageGeneratorService pageGeneratorService;

    public List<ObjectType> list() {
        return objectTypeRepository.findAllByOrderBySortOrderAsc();
    }

    public ObjectType get(String key) {
        return objectTypeOrThrow(key);
    }

    @Transactional
    public ObjectType create(CreateObjectTypeDto dto) {
        if (objectTypeRepository.findByKey(dto.getKey()).isPresent()) {
            throw new BadRequestException("Chiave già esistente");
        }
        ObjectType obj = new ObjectType();
        obj.setKey(dto.getKey());
        obj.setLabel(dto.getLabel());
        obj.setPluralLabel(dto.getPluralLabel());
        obj.setIcon(dto.getIcon());
        obj.setColor(dto.getColor());
        obj.setSortOrder((int) objectTypeRepository.count());
        objectTypeRepository.save(obj);

        if (dto.getFields() != null) {
            int order = 0;
            for (FieldDefDto f : dto.getFields()) {
                FieldDef field = buildFieldDef(obj, f, order++);
                fieldDefRepository.save(field);
                // l'istanza obj resta quella nel persistence context per il resto della
                // transazione (identity map): senza questa add, il findByKey successivo
                // ritornerebbe la stessa istanza con fields ancora vuoto
                obj.getFields().add(field);
            }
        }

        pageGeneratorService.generate(obj.getId());
        return objectTypeOrThrow(obj.getKey());
    }

    @Transactional
    public ObjectType update(String key, UpdateObjectTypeDto dto) {
        ObjectType obj = objectTypeOrThrow(key);
        if (dto.getLabel() != null) obj.setLabel(dto.getLabel());
        if (dto.getPluralLabel() != null) obj.setPluralLabel(dto.getPluralLabel());
        if (dto.getIcon() != null) obj.setIcon(dto.getIcon());
        if (dto.getColor() != null) obj.setColor(dto.getColor());
        if (dto.getIsEnabled() != null) obj.setEnabled(dto.getIsEnabled());
        if (dto.getSortOrder() != null) obj.setSortOrder(dto.getSortOrder());
        if (dto.getPublicForm() != null) obj.setPublicForm(dto.getPublicForm());
        objectTypeRepository.save(obj);
        return objectTypeOrThrow(key);
    }

    @Transactional
    public void remove(String key) {
        ObjectType obj = objectTypeOrThrow(key);
        if (obj.isSystem()) {
            throw new BadRequestException("Gli object type di sistema non sono eliminabili");
        }
        objectTypeRepository.delete(obj);
    }

    @Transactional
    public ObjectType addField(String key, FieldDefDto dto) {
        assertValidPattern(dto.getPattern());
        ObjectType obj = objectTypeOrThrow(key);
        int order = obj.getFields().size();
        FieldDef field = buildFieldDef(obj, dto, order);
        fieldDefRepository.save(field);
        obj.getFields().add(field);
        pageGeneratorService.generate(obj.getId());
        return objectTypeOrThrow(key);
    }

    @Transactional
    public ObjectType updateField(String key, String fieldKey, FieldDefDto dto) {
        assertValidPattern(dto.getPattern());
        ObjectType obj = objectTypeOrThrow(key);
        FieldDef field = fieldOrThrow(obj, fieldKey);
        // i campi obbligatori sono "di base" e non si toccano: né si modificano né si eliminano
        if (field.isRequired()) {
            throw new BadRequestException("I campi obbligatori non si possono modificare");
        }
        applyFieldUpdates(field, dto);
        fieldDefRepository.save(field);
        return objectTypeOrThrow(key);
    }

    @Transactional
    public ObjectType removeField(String key, String fieldKey) {
        ObjectType obj = objectTypeOrThrow(key);
        FieldDef field = fieldOrThrow(obj, fieldKey);
        if (field.isRequired()) {
            throw new BadRequestException("I campi obbligatori non si possono eliminare");
        }
        fieldDefRepository.delete(field);
        return objectTypeOrThrow(key);
    }

    private ObjectType objectTypeOrThrow(String key) {
        return objectTypeRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Object type '" + key + "' non trovato"));
    }

    private FieldDef fieldOrThrow(ObjectType obj, String fieldKey) {
        return fieldDefRepository.findByObjectType_IdAndKey(obj.getId(), fieldKey)
                .orElseThrow(() -> new NotFoundException("Field '" + fieldKey + "' non trovato"));
    }

    private FieldDef buildFieldDef(ObjectType obj, FieldDefDto f, int order) {
        FieldDef field = new FieldDef();
        field.setObjectType(obj);
        field.setKey(f.getKey());
        field.setLabel(f.getLabel());
        field.setType(f.getType());
        field.setDescription(f.getDescription());
        field.setPlaceholder(f.getPlaceholder());
        field.setRequired(Boolean.TRUE.equals(f.getRequired()));
        field.setUnique(Boolean.TRUE.equals(f.getIsUnique()));
        field.setIndexed(Boolean.TRUE.equals(f.getIsIndexed()));
        field.setReadonly(Boolean.TRUE.equals(f.getIsReadonly()));
        field.setHidden(Boolean.TRUE.equals(f.getIsHidden()));
        field.setFilterable(f.getIsFilterable() == null || f.getIsFilterable());
        field.setSortable(f.getIsSortable() == null || f.getIsSortable());
        field.setShowInList(f.getShowInList() == null || f.getShowInList());
        field.setDefaultValue(f.getDefaultValue());
        field.setMin(f.getMin());
        field.setMax(f.getMax());
        field.setStep(f.getStep());
        field.setPattern(f.getPattern());
        field.setSection(f.getSection());
        field.setWidth(f.getWidth() != null ? f.getWidth() : "full");
        field.setOptions(f.getOptions());
        field.setConfig(f.getConfig());
        field.setIcon(f.getIcon());
        field.setSortOrder(f.getSortOrder() != null ? f.getSortOrder() : order);
        return field;
    }

    // il pattern regex arriva dall'admin via UI: sintassi e lunghezza vanno verificate subito al
    // salvataggio (400 chiaro), non scoperte a runtime alla prima validazione di un record.
    // A runtime resta comunque la guardia SafeRegex (deadline anti-ReDoS) in RecordValidator.
    private void assertValidPattern(String pattern) {
        String error = SafeRegex.validationError(pattern);
        if (error != null) {
            throw new BadRequestException("Pattern: " + error);
        }
    }

    private void applyFieldUpdates(FieldDef field, FieldDefDto dto) {
        if (dto.getLabel() != null) field.setLabel(dto.getLabel());
        if (dto.getType() != null) field.setType(dto.getType());
        if (dto.getDescription() != null) field.setDescription(dto.getDescription());
        if (dto.getPlaceholder() != null) field.setPlaceholder(dto.getPlaceholder());
        if (dto.getRequired() != null) field.setRequired(dto.getRequired());
        if (dto.getIsUnique() != null) field.setUnique(dto.getIsUnique());
        if (dto.getIsIndexed() != null) field.setIndexed(dto.getIsIndexed());
        if (dto.getIsReadonly() != null) field.setReadonly(dto.getIsReadonly());
        if (dto.getIsHidden() != null) field.setHidden(dto.getIsHidden());
        if (dto.getIsFilterable() != null) field.setFilterable(dto.getIsFilterable());
        if (dto.getIsSortable() != null) field.setSortable(dto.getIsSortable());
        if (dto.getShowInList() != null) field.setShowInList(dto.getShowInList());
        if (dto.getDefaultValue() != null) field.setDefaultValue(dto.getDefaultValue());
        if (dto.getMin() != null) field.setMin(dto.getMin());
        if (dto.getMax() != null) field.setMax(dto.getMax());
        if (dto.getStep() != null) field.setStep(dto.getStep());
        if (dto.getPattern() != null) field.setPattern(dto.getPattern());
        if (dto.getSection() != null) field.setSection(dto.getSection());
        if (dto.getWidth() != null) field.setWidth(dto.getWidth());
        if (dto.getOptions() != null) field.setOptions(dto.getOptions());
        if (dto.getConfig() != null) field.setConfig(dto.getConfig());
        if (dto.getSortOrder() != null) field.setSortOrder(dto.getSortOrder());
        if (dto.getIcon() != null) field.setIcon(dto.getIcon());
    }
}
