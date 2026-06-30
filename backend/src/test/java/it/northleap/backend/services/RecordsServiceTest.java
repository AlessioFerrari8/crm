package it.northleap.backend.services;

import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.ObjectTypeRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import it.northleap.backend.exceptions.RbacDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Copre in particolare resolveOwnerId: sotto scope OWN un attore non deve poter intestare/
// riassegnare un record a un ownerId arbitrario passato nel body, a prescindere da cosa manda
// il client - gap ereditato dall'originale (records.service.ts usava sempre `dto.ownerId ??
// actor.id` senza guardare lo scope), chiuso qui deliberatamente.
@ExtendWith(MockitoExtension.class)
class RecordsServiceTest {

    @Mock
    private ObjectTypeRepository objectTypeRepository;
    @Mock
    private RecordRepository recordRepository;
    @Mock
    private RecordLinkRepository recordLinkRepository;
    @Mock
    private RecordQueryService recordQueryService;
    @Mock
    private RbacService rbacService;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher events;

    private RecordsService service;
    private ObjectType objectType;

    @BeforeEach
    void setUp() {
        service = new RecordsService(objectTypeRepository, recordRepository, recordLinkRepository,
                new RecordValidator(), recordQueryService, rbacService, auditService, events);
        objectType = new ObjectType();
        objectType.setId(UUID.randomUUID());
        objectType.setKey("lead");
        objectType.setFields(List.of());
        lenient().when(objectTypeRepository.findByKey("lead")).thenReturn(Optional.of(objectType));
        lenient().when(recordRepository.save(any(Record.class))).thenAnswer(inv -> {
            Record r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
    }

    private Actor actor(UUID id) {
        return new Actor(id, ActorType.USER, "u@test.com", List.of(UUID.randomUUID()));
    }

    private UpsertRecordDto dto(UUID ownerId) {
        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of());
        dto.setOwnerId(ownerId);
        return dto;
    }

    @Test
    void createUnderOwnScopeIgnoresClientSuppliedOwnerId() {
        UUID actorId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.OWN));

        Record created = service.create(actor(actorId), "lead", dto(someoneElse), null);

        assertEquals(actorId, created.getOwnerId());
    }

    @Test
    void createUnderAllScopeHonorsClientSuppliedOwnerId() {
        UUID actorId = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));

        Record created = service.create(actor(actorId), "lead", dto(assignee), null);

        assertEquals(assignee, created.getOwnerId());
    }

    @Test
    void createUnderAllScopeDefaultsToActorWhenNoOwnerIdGiven() {
        UUID actorId = UUID.randomUUID();
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));

        Record created = service.create(actor(actorId), "lead", dto(null), null);

        assertEquals(actorId, created.getOwnerId());
    }

    @Test
    void updateUnderOwnScopeIgnoresReassignmentAttempt() {
        UUID actorId = UUID.randomUUID();
        UUID hijackTarget = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(actorId);
        existing.setData(Map.of());
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.OWN));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        Record updated = service.update(actor(actorId), "lead", existing.getId(), dto(hijackTarget), null);

        assertEquals(actorId, updated.getOwnerId());
    }

    @Test
    void updateUnderAllScopeAllowsReassignment() {
        UUID actorId = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(actorId);
        existing.setData(Map.of());
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        Record updated = service.update(actor(actorId), "lead", existing.getId(), dto(newOwner), null);

        assertEquals(newOwner, updated.getOwnerId());
    }

    @Test
    void removeUnderOwnScopeDeniesSomeoneElsesRecord() {
        UUID actorId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(otherOwner);
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.OWN));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        assertThrows(RbacDeniedException.class,
                () -> service.remove(actor(actorId), "lead", existing.getId(), null));
        assertFalse(existing.isDeleted());
        verify(recordRepository, never()).save(any(Record.class));
    }

    @Test
    void removeUnderOwnScopeAllowsOwnRecord() {
        UUID actorId = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(actorId);
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.OWN));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        service.remove(actor(actorId), "lead", existing.getId(), null);

        assertTrue(existing.isDeleted());
    }

    @Test
    void removeUnderAllScopeAllowsSomeoneElsesRecord() {
        UUID actorId = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(otherOwner);
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        service.remove(actor(actorId), "lead", existing.getId(), null);

        assertTrue(existing.isDeleted());
    }
}
