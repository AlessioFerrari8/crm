package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateLinkDto;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.PermAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationsServiceTest {

    @Mock
    private RecordRepository recordRepository;
    @Mock
    private RecordLinkRepository recordLinkRepository;
    @Mock
    private RbacService rbacService;

    private RelationsService service;
    private Actor actor;

    @BeforeEach
    void setUp() {
        service = new RelationsService(recordRepository, recordLinkRepository, rbacService);
        actor = new Actor(UUID.randomUUID(), ActorType.USER, "u@test.com", List.of(UUID.randomUUID()));
    }

    private Record record(String objectKey) {
        ObjectType obj = new ObjectType();
        obj.setKey(objectKey);
        Record r = new Record();
        r.setId(UUID.randomUUID());
        r.setObjectType(obj);
        return r;
    }

    @Test
    void createDeniesWhenRbacWriteNotAllowed() {
        Record source = record("contact");
        when(recordRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(rbacService.resolve(actor.roleIds(), "contact", PermAction.WRITE))
                .thenReturn(new RbacService.Resolution(false, null));

        CreateLinkDto dto = new CreateLinkDto();
        dto.setTargetId(UUID.randomUUID());
        dto.setRelationKey("contact.company");

        assertThrows(RbacDeniedException.class, () -> service.create(actor, source.getId(), dto));
        verify(recordLinkRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createUpsertsExistingLinkInsteadOfDuplicating() {
        Record source = record("contact");
        UUID targetId = UUID.randomUUID();
        when(recordRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, null));
        when(recordRepository.findById(targetId)).thenReturn(Optional.of(record("company")));

        RecordLink existing = new RecordLink();
        existing.setId(UUID.randomUUID());
        when(recordLinkRepository.findBySource_IdAndTarget_IdAndRelationKey(source.getId(), targetId, "contact.company"))
                .thenReturn(Optional.of(existing));

        CreateLinkDto dto = new CreateLinkDto();
        dto.setTargetId(targetId);
        dto.setRelationKey("contact.company");
        RecordLink result = service.create(actor, source.getId(), dto);

        assertEquals(existing.getId(), result.getId());
        verify(recordLinkRepository).save(existing);
    }

    @Test
    void removeChecksWriteOnSourceRecord() {
        Record source = record("contact");
        RecordLink link = new RecordLink();
        link.setId(UUID.randomUUID());
        link.setSource(source);
        when(recordLinkRepository.findById(link.getId())).thenReturn(Optional.of(link));
        when(recordRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, null));

        service.remove(actor, link.getId());

        verify(recordLinkRepository).delete(link);
    }

    @Test
    void listMergesOutgoingAndIncoming() {
        UUID recordId = UUID.randomUUID();
        RecordLink out = new RecordLink();
        RecordLink in = new RecordLink();
        // finder filtrati: i link verso/da record cestinati non compaiono qui (restano a DB)
        when(recordLinkRepository.findBySource_IdAndTarget_IsDeletedFalse(recordId)).thenReturn(List.of(out));
        when(recordLinkRepository.findByTarget_IdAndSource_IsDeletedFalse(recordId)).thenReturn(List.of(in));

        List<RecordLink> result = service.list(recordId);

        assertEquals(2, result.size());
        verify(recordLinkRepository, never()).findBySource_Id(recordId);
        verify(recordLinkRepository, never()).findByTarget_Id(recordId);
    }
}
