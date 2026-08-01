package it.northleap.backend.services;

import it.northleap.backend.dtos.BulkAction;
import it.northleap.backend.dtos.BulkRecordsRequest;
import it.northleap.backend.dtos.BulkResult;
import it.northleap.backend.dtos.QueryRecordsDto;
import it.northleap.backend.dtos.RecordDetailResponse;
import it.northleap.backend.dtos.RecordQueryResponse;
import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.events.RecordCreatedEvent;
import it.northleap.backend.events.RecordDeletedEvent;
import it.northleap.backend.events.RecordUpdatedEvent;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.exceptions.RecordValidationException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// Porting di records.service.ts: CRUD generico su Record, qualunque sia l'ObjectType. L'RBAC qui
// NON passa da @RequirePerm (la risorsa è il {key} dinamico, non noto a tempo di annotazione) —
// si chiama RbacService.resolve(...) direttamente, come authorize() nell'originale.
@Service
@RequiredArgsConstructor
public class RecordsService {

    private final ObjectTypeRepository objectTypeRepository;
    private final RecordRepository recordRepository;
    private final RecordLinkRepository recordLinkRepository;
    private final RecordValidator recordValidator;
    private final RecordQueryService recordQueryService;
    private final RbacService rbacService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public RecordQueryResponse query(Actor actor, String key, QueryRecordsDto q) {
        PermScope scope = authorize(actor, key, PermAction.READ);
        ObjectType obj = objectOrThrow(key);
        UUID ownerScopeId = actor.type() == ActorType.USER ? actor.id() : null;

        RecordQueryService.QueryResult result = recordQueryService.query(obj, q, scope, ownerScopeId);
        return new RecordQueryResponse(result.items(), result.total(), result.page(), result.pageSize(), obj);
    }

    public RecordDetailResponse findOne(Actor actor, String key, UUID id) {
        ObjectType obj = objectOrThrow(key);
        authorize(actor, key, PermAction.READ);
        Record record = recordRepository.findByIdAndIsDeletedFalse(id)
                .filter(r -> r.getObjectType().getId().equals(obj.getId()))
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
        // i link verso/da record cestinati restano a DB ma non si mostrano (vedi RecordLinkRepository)
        List<RecordLink> outgoing = recordLinkRepository.findBySource_IdAndTarget_IsDeletedFalse(id);
        List<RecordLink> incoming = recordLinkRepository.findByTarget_IdAndSource_IsDeletedFalse(id);
        return new RecordDetailResponse(record, outgoing, incoming);
    }

    @Transactional
    public Record create(Actor actor, String key, UpsertRecordDto dto, String ip) {
        PermScope scope = authorize(actor, key, PermAction.WRITE);
        ObjectType obj = objectOrThrow(key);
        Map<String, Object> data = recordValidator.validate(obj.getFields(), dto.getData());
        assertUniqueConstraints(obj, data, null);

        Record record = new Record();
        record.setObjectType(obj);
        record.setTitle(dto.getTitle() != null ? dto.getTitle() : recordValidator.deriveTitle(obj.getFields(), data));
        record.setStatus(dto.getStatus() != null ? dto.getStatus() : recordValidator.deriveStatus(obj.getFields(), data));
        record.setOwnerId(resolveOwnerId(actor, scope, dto.getOwnerId(), null));
        record.setData(data);
        recordRepository.save(record);
        syncRelationLinks(obj, record, data);

        auditService.log(actor, "create", key, record.getId().toString(), null, ip);
        events.publishEvent(new RecordCreatedEvent(key, record));
        return record;
    }

    @Transactional
    public Record update(Actor actor, String key, UUID id, UpsertRecordDto dto, String ip) {
        PermScope scope = authorize(actor, key, PermAction.WRITE);
        ObjectType obj = objectOrThrow(key);
        Record existing = recordRepository.findByIdAndIsDeletedFalse(id)
                .filter(r -> r.getObjectType().getId().equals(obj.getId()))
                .orElseThrow(() -> new NotFoundException("Record non trovato"));

        assertOwnership(scope, actor, existing, "Puoi modificare solo i tuoi record");

        Map<String, Object> merged = new LinkedHashMap<>(existing.getData());
        merged.putAll(dto.getData());
        Map<String, Object> data = recordValidator.validate(obj.getFields(), merged);
        assertUniqueConstraints(obj, data, existing.getId());

        Map<String, Object> before = existing.getData();
        String beforeStatus = existing.getStatus();
        existing.setTitle(dto.getTitle() != null ? dto.getTitle() : recordValidator.deriveTitle(obj.getFields(), data));
        existing.setStatus(dto.getStatus() != null ? dto.getStatus() : recordValidator.deriveStatus(obj.getFields(), data));
        existing.setOwnerId(resolveOwnerId(actor, scope, dto.getOwnerId(), existing.getOwnerId()));
        existing.setData(data);
        recordRepository.save(existing);
        syncRelationLinks(obj, existing, data);

        auditService.log(actor, "update", key, id.toString(), Map.of("before", before, "after", data), ip);
        events.publishEvent(new RecordUpdatedEvent(key, existing, before, beforeStatus));
        return existing;
    }

    @Transactional
    public void remove(Actor actor, String key, UUID id, String ip) {
        PermScope scope = authorize(actor, key, PermAction.WRITE);
        ObjectType obj = objectOrThrow(key);
        Record existing = recordRepository.findByIdAndIsDeletedFalse(id)
                .filter(r -> r.getObjectType().getId().equals(obj.getId()))
                .orElseThrow(() -> new NotFoundException("Record non trovato"));

        // stessa enforcement OWN-scope di update()/bulk(): senza questo un attore con WRITE in
        // scope OWN poteva cancellare (soft-delete) record altrui — lo scope qui veniva ignorato
        assertOwnership(scope, actor, existing, "Puoi eliminare solo i tuoi record");

        existing.setDeleted(true);
        recordRepository.save(existing);

        auditService.log(actor, "delete", key, id.toString(), null, ip);
        events.publishEvent(new RecordDeletedEvent(key, existing));
    }

    @Transactional
    public BulkResult bulk(Actor actor, String key, BulkRecordsRequest req, String ip) {
        PermScope scope = authorize(actor, key, PermAction.WRITE);
        ObjectType obj = objectOrThrow(key);
        List<UUID> ids = req.getIds().size() > 500 ? req.getIds().subList(0, 500) : req.getIds();
        boolean ownScope = scope == PermScope.OWN && actor.type() == ActorType.USER;

        List<Record> records = ownScope
                ? recordRepository.findByIdInAndObjectType_IdAndOwnerIdAndIsDeletedFalse(ids, obj.getId(), actor.id())
                : recordRepository.findByIdInAndObjectType_IdAndIsDeletedFalse(ids, obj.getId());

        if (req.getAction() == BulkAction.DELETE) {
            records.forEach(r -> r.setDeleted(true));
            recordRepository.saveAll(records);
            auditService.log(actor, "bulk_delete", key, null, Map.of("ids", ids), ip);
            return new BulkResult(records.size());
        }

        int affected = 0;
        for (Record r : records) {
            Map<String, Object> merged = new LinkedHashMap<>(r.getData());
            if (req.getSet() != null) {
                merged.putAll(req.getSet());
            }
            Map<String, Object> data = recordValidator.validate(obj.getFields(), merged);
            // anche in bulk: impostare un campo unico allo stesso valore su piu' record deve
            // fallire dal secondo in poi, non creare duplicati in silenzio
            assertUniqueConstraints(obj, data, r.getId());
            r.setData(data);
            r.setTitle(recordValidator.deriveTitle(obj.getFields(), data));
            r.setStatus(recordValidator.deriveStatus(obj.getFields(), data));
            recordRepository.save(r);
            affected++;
        }
        auditService.log(actor, "bulk_update", key, null,
                Map.of("ids", records.stream().map(r -> r.getId().toString()).toList(), "set", req.getSet() != null ? req.getSet() : Map.of()), ip);
        return new BulkResult(affected);
    }

    // cestino: gli ultimi 50 record soft-deleted del tipo (sotto scope OWN solo i propri)
    public List<Record> trash(Actor actor, String key) {
        PermScope scope = authorize(actor, key, PermAction.READ);
        ObjectType obj = objectOrThrow(key);
        if (scope == PermScope.OWN && actor.type() == ActorType.USER) {
            return recordRepository.findTop50ByObjectType_IdAndOwnerIdAndIsDeletedTrueOrderByUpdatedAtDesc(
                    obj.getId(), actor.id());
        }
        return recordRepository.findTop50ByObjectType_IdAndIsDeletedTrueOrderByUpdatedAtDesc(obj.getId());
    }

    // ripristino dal cestino: inverso del soft-delete di remove(). Nessun evento pubblicato:
    // ripristinare non è creare (i workflow "record.created" non devono riscattare)
    @Transactional
    public Record restore(Actor actor, String key, UUID id, String ip) {
        PermScope scope = authorize(actor, key, PermAction.WRITE);
        ObjectType obj = objectOrThrow(key);
        Record record = recordRepository.findById(id)
                .filter(Record::isDeleted)
                .filter(r -> r.getObjectType().getId().equals(obj.getId()))
                .orElseThrow(() -> new NotFoundException("Record non trovato nel cestino"));

        assertOwnership(scope, actor, record, "Puoi ripristinare solo i tuoi record");

        record.setDeleted(false);
        recordRepository.save(record);
        auditService.log(actor, "restore", key, id.toString(), null, ip);
        return record;
    }

    public List<Record> globalSearch(Actor actor, String q) {
        if (q == null || q.length() < 2) {
            return List.of();
        }
        List<Record> candidates = recordRepository.findByIsDeletedFalseAndTitleContainingOrderByUpdatedAtDesc(q)
                .stream().limit(30).toList();

        Map<UUID, RbacService.Resolution> cache = new LinkedHashMap<>();
        return candidates.stream()
                .filter(r -> {
                    RbacService.Resolution res = cache.computeIfAbsent(r.getObjectType().getId(),
                            oid -> rbacService.resolve(actor.roleIds(), r.getObjectType().getKey(), PermAction.READ));
                    if (!res.allowed()) {
                        return false;
                    }
                    // sotto scope OWN la ricerca globale non deve esporre i titoli dei record altrui
                    // (stesso confine record-level che query() applica via owner_id)
                    if (res.scope() == PermScope.OWN && actor.type() == ActorType.USER) {
                        return actor.id().equals(r.getOwnerId());
                    }
                    return true;
                })
                .toList();
    }

    // L'originale (records.service.ts) usa sempre `dto.ownerId ?? actor.id` a prescindere dallo
    // scope: un attore con permesso WRITE solo scope OWN poteva comunque intestare un record
    // (in create) o riassegnarlo (in update) a un ownerId arbitrario passato nel body, scavalcando
    // il senso stesso dello scope OWN. Qui chiudiamo deliberatamente questo gap: sotto scope OWN
    // l'ownerId è sempre forzato all'attore stesso, qualunque cosa il client mandi. Per scope
    // ALL/TEAM il comportamento resta quello originale (un admin/manager può assegnare/riassegnare
    // la proprietà di un record ad altri, es. assegnare un lead a un agente specifico).
    // rende effettivo FieldDef.isUnique (prima era un flag solo dichiarativo): nessun altro
    // record non cancellato dello stesso tipo puo' avere lo stesso valore. Confronto testuale
    // via data->>key (vedi RecordQueryService.hasDuplicateValue); i null/vuoti non contano.
    private void assertUniqueConstraints(ObjectType obj, Map<String, Object> data, UUID excludeId) {
        for (FieldDef f : obj.getFields()) {
            if (!f.isUnique()) {
                continue;
            }
            Object value = data.get(f.getKey());
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }
            if (recordQueryService.hasDuplicateValue(obj.getId(), f.getKey(), String.valueOf(value), excludeId)) {
                throw new RecordValidationException(f.getLabel() + ": valore già esistente");
            }
        }
    }

    private UUID resolveOwnerId(Actor actor, PermScope scope, UUID requestedOwnerId, UUID currentOwnerId) {
        if (scope == PermScope.OWN && actor.type() == ActorType.USER) {
            return actor.id();
        }
        if (requestedOwnerId != null) {
            return requestedOwnerId;
        }
        if (currentOwnerId != null) {
            return currentOwnerId;
        }
        return actor.type() == ActorType.USER ? actor.id() : null;
    }

    // Enforcement record-level dello scope OWN: un attore USER con permesso solo OWN può agire
    // solo sui propri record. Per scope ALL/TEAM (o attori non-USER) non si applica.
    private void assertOwnership(PermScope scope, Actor actor, Record rec, String message) {
        if (scope == PermScope.OWN && actor.type() == ActorType.USER
                && !actor.id().equals(rec.getOwnerId())) {
            throw new RbacDeniedException(message);
        }
    }

    private PermScope authorize(Actor actor, String key, PermAction action) {
        RbacService.Resolution resolution = rbacService.resolve(actor.roleIds(), key, action);
        if (!resolution.allowed()) {
            throw new RbacDeniedException("Permesso negato: " + action + " su " + key);
        }
        return resolution.scope();
    }

    private ObjectType objectOrThrow(String key) {
        return objectTypeRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Object type '" + key + "' non trovato"));
    }

    private void syncRelationLinks(ObjectType obj, Record record, Map<String, Object> data) {
        for (FieldDef f : obj.getFields()) {
            if (f.getType() != FieldType.RELATION) continue;

            String relationKey = obj.getKey() + "." + f.getKey();
            List<UUID> targets = extractIds(data.get(f.getKey()));

            List<RecordLink> existing = recordLinkRepository.findBySource_Id(record.getId()).
                    stream()
                    .filter(l -> relationKey.equals(l.getRelationKey()))
                    .toList();

            existing.stream()
                    .filter(l -> !targets.contains(l.getTarget().getId()))
                    .forEach(recordLinkRepository::delete);

            for (UUID targetId : targets) {
                if (recordLinkRepository
                        .findBySource_IdAndTarget_IdAndRelationKey(record.getId(), targetId, relationKey).isPresent()) {
                    continue;
                }

                // target inesistente o cestinato
                recordRepository.findByIdAndIsDeletedFalse(targetId).ifPresent(target -> {
                    RecordLink link = new RecordLink();
                    link.setSource(record);
                    link.setTarget(target);
                    link.setRelationKey(relationKey);
                    recordLinkRepository.save(link);
                });
            }

        }
    }


    @SuppressWarnings("unchecked")
    private List<UUID> extractIds(Object value) {
        if (value == null) return List.of();

        List<Object> raw = value instanceof List<?> list ? (List<Object>) list : List.of(value);
        return raw.stream()
                .map(String::valueOf)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return UUID.fromString(s);
                    } catch (IllegalArgumentException e) {
                        return null; // valore non UUID in data
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

}
