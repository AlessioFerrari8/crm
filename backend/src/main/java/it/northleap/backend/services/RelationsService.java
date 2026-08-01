package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateLinkDto;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.PermAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Porting di RelationsService (relations.module.ts): CRUD sui RecordLink. RBAC dinamico come in
// RecordsService - la risorsa è l'objectType.key del record sorgente, non nota a tempo di
// annotazione, quindi niente @RequirePerm qui.
@Service
@RequiredArgsConstructor
public class RelationsService {

    private final RecordRepository recordRepository;
    private final RecordLinkRepository recordLinkRepository;
    private final RbacService rbacService;

    // stesso filtro di RecordsService.findOne: i collegamenti verso record cestinati non si
    // mostrano, ma restano a DB per il ripristino (vedi RecordLinkRepository)
    public List<RecordLink> list(UUID recordId) {
        List<RecordLink> links = new ArrayList<>(recordLinkRepository.findBySource_IdAndTarget_IsDeletedFalse(recordId));
        links.addAll(recordLinkRepository.findByTarget_IdAndSource_IsDeletedFalse(recordId));
        return links;
    }

    @Transactional
    public RecordLink create(Actor actor, UUID sourceId, CreateLinkDto dto) {
        Record source = assertWrite(actor, sourceId);
        // findById, non getReferenceById: il link viene serializzato così com'è nella risposta
        // HTTP, e un proxy Hibernate non inizializzato (getReferenceById) trapela la proprietà
        // interna hibernateLazyInitializer nel JSON quando Jackson lo attraversa - trovato in
        // smoke test live, non un problema teorico
        Record target = recordRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new NotFoundException("Record target non trovato"));

        RecordLink link = recordLinkRepository
                .findBySource_IdAndTarget_IdAndRelationKey(sourceId, dto.getTargetId(), dto.getRelationKey())
                .orElseGet(RecordLink::new);
        link.setSource(source);
        link.setTarget(target);
        link.setRelationKey(dto.getRelationKey());
        link.setData(dto.getData());
        recordLinkRepository.save(link);
        return link;
    }

    @Transactional
    public void remove(Actor actor, UUID linkId) {
        RecordLink link = recordLinkRepository.findById(linkId)
                .orElseThrow(() -> new NotFoundException("Link non trovato"));
        assertWrite(actor, link.getSource().getId());
        recordLinkRepository.delete(link);
    }

    private Record assertWrite(Actor actor, UUID recordId) {
        Record record = recordRepository.findById(recordId)
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
        RbacService.Resolution resolution = rbacService.resolve(actor.roleIds(), record.getObjectType().getKey(), PermAction.WRITE);
        if (!resolution.allowed()) {
            throw new RbacDeniedException();
        }
        return record;
    }
}
