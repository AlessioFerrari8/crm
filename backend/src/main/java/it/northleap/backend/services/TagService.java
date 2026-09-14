package it.northleap.backend.services;


import it.northleap.backend.dtos.CreateTagDto;
import it.northleap.backend.entities.Tag;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordTag;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.RecordTagRepository;
import it.northleap.backend.repositories.TagRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    // inject
    private final TagRepository tagRepository;
    private final RecordTagRepository recordTagRepository;
    private final RecordRepository recordRepository;
    private final RbacService rbacService;

    public List<Tag> list(Actor actor) {
        return tagRepository.findVisible(actor.id());
    }

    @Transactional
    public Tag create(Actor actor, CreateTagDto dto) {
        UUID ownerId = dto.isPrivate() ? actor.id() : null;
        // il trim va fatto PRIMA del controllo: cercando il nome grezzo, " lavoro" non
        // troverebbe "lavoro" e il duplicato lo intercetterebbe il vincolo UNIQUE, con un
        // 500 al posto del 400
        String name = dto.name().trim();

        // il vincolo UNIQUE non copre i pubblici (owner_id NULL non collide con NULL
        // in postgres), quindi il duplicato lo intercetto qui
        boolean exists = ownerId == null
            ? tagRepository.existsByNameAndOwnerIdIsNull(name)
            : tagRepository.existsByNameAndOwnerId(name, ownerId);

        if (exists) {
            throw new BadRequestException("Tag already exists");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(dto.color());
        tag.setOwnerId(ownerId);
        return tagRepository.save(tag);
    }

    @Transactional
    public void remove(Actor actor, UUID tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException("Tag not found"));

        // un tag privato lo cancella solo il proprietario; per i pubblici serve
        // il permesso di scrivere sui tipi -> lo tratto come impostazione di workspace
        // e chiedo WRITE su "objectType"
        if (tag.getOwnerId() != null && !tag.getOwnerId().equals(actor.id())) {
            throw new RbacDeniedException("Cannot delete a private tag or another user");
        }

        if (tag.getOwnerId() == null) {
            assertWorkspaceAdmin(actor);
        }

        // le associazioni cadono con il tag: sono etichette, non dati del record
        recordTagRepository.deleteByTagId(tagId);

        tagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<Tag> ofRecord(Actor actor, UUID recordId) {
        assertRecordPerm(actor, recordId, PermAction.READ);
        return recordTagRepository.findByRecordId(recordId).stream()
                .map(RecordTag::getTag)
                // i tag privati di altri utenti non devono comparire
                .filter(t -> t.getOwnerId() == null || t.getOwnerId().equals(actor.id()))
                .toList();
    }

    @Transactional
    public void attach(Actor actor, UUID recordId, UUID tagId) {
        Record record = assertRecordPerm(actor, recordId, PermAction.WRITE);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException("Tag not found"));

        if (tag.getOwnerId() != null && !tag.getOwnerId().equals(actor.id())) {
            throw new RbacDeniedException("Cannot use a private tag of another user");
        }

        // idempotente: riattaccare lo stesso tag non deve dare errore, il
        // frontend fa toggle e un doppio click non è un caso di errore
        boolean already = recordTagRepository.findByRecordId(recordId).stream()
                .anyMatch(rt -> rt.getTag().getId().equals(tagId));

        if (already) return;

        RecordTag link = new RecordTag();
        link.setRecord(record);
        link.setTag(tag);
        recordTagRepository.save(link);
    }

    @Transactional
    public void detach(Actor actor, UUID recordId, UUID tagId) {
        assertRecordPerm(actor, recordId, PermAction.WRITE);
        recordTagRepository.deleteByRecordIdAndTagId(recordId, tagId);
    }

    // readOnly perché r.getObjectType() è LAZY: senza transazione funziona solo grazie a
    // open-in-view, che è un default e può essere spento
    @Transactional(readOnly = true)
    public List<Record> recordsWithTag(Actor actor, UUID tagId) {
        // il filtro RBAC per-modulo va applicato qui: un tag attraversa i moduli,
        // e l'utente potrebbe vedere i ticket, ma non le opportunità
        return recordTagRepository.findRecordsByTagId(tagId).stream()
                .filter(r -> rbacService.resolve(actor.roleIds(),
                        r.getObjectType().getKey(), PermAction.READ).allowed())
                .toList();
    }

    private Record assertRecordPerm(Actor actor, UUID recordId, PermAction action) {
        Record record = recordRepository.findById(recordId)
                .orElseThrow(() -> new NotFoundException("Record not found"));

        RbacService.Resolution resolution =
                rbacService.resolve(actor.roleIds(), record.getObjectType().getKey(), action);

        if (!resolution.allowed()) {
            throw new RbacDeniedException("Not allowed on " + record.getObjectType().getKey());
        }
        return record;
    }

    private void assertWorkspaceAdmin(Actor actor) {
        if (!rbacService.resolve(actor.roleIds(), "objecttype", PermAction.WRITE).allowed()) {
            throw new RbacDeniedException("Not allowed to manage shared tags");
        }
    }

    // versione in blocco di `ofRecord()`: stessi due controlli, ma il permesso si risolve una volta per modulo
    // e non una per record, sennò la lista da 200 righe diventa 200 righi di RBAC
    @Transactional(readOnly = true)
    public Map<UUID, List<Tag>> ofRecords(Actor actor, Collection<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Boolean> allowedByObject = new HashMap<>();

        return recordTagRepository.findByRecordIdIn(recordIds).stream()
                // RBAC per modulo, memorizzato: un tag attraversa i moduli e
                // l'utente potrebbe vedere i ticket ma non le opportunità
                .filter(rt -> allowedByObject.computeIfAbsent(
                        rt.getRecord().getObjectType().getKey(),
                        key -> rbacService.resolve(actor.roleIds(), key, PermAction.READ).allowed()))
                // i tag privati di altri utenti non devono comparire (come in ofRecord)
                .filter(rt -> rt.getTag().getOwnerId() == null
                        || rt.getTag().getOwnerId().equals(actor.id()))
                .collect(Collectors.groupingBy(
                        rt -> rt.getRecord().getId(),
                        Collectors.mapping(RecordTag::getTag, Collectors.toList())));
    }

}
