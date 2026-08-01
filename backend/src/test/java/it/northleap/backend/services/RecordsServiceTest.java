package it.northleap.backend.services;

import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;
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
import it.northleap.backend.exceptions.RecordValidationException;

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

    // ── enforcement di FieldDef.isUnique ─────────────────────────────────────

    private void givenUniqueEmailField() {
        FieldDef email = new FieldDef();
        email.setKey("email");
        email.setLabel("Email");
        email.setType(FieldType.TEXT);
        email.setUnique(true);
        objectType.setFields(List.of(email));
    }

    @Test
    void createRejectsDuplicateValueOnUniqueField() {
        givenUniqueEmailField();
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordQueryService.hasDuplicateValue(objectType.getId(), "email", "a@b.it", null)).thenReturn(true);

        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("email", "a@b.it"));

        assertThrows(RecordValidationException.class,
                () -> service.create(actor(UUID.randomUUID()), "lead", dto, null));
        verify(recordRepository, never()).save(any(Record.class));
    }

    @Test
    void createAllowsUniqueValueWhenNoDuplicateExists() {
        givenUniqueEmailField();
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordQueryService.hasDuplicateValue(objectType.getId(), "email", "a@b.it", null)).thenReturn(false);

        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("email", "a@b.it"));

        Record created = service.create(actor(UUID.randomUUID()), "lead", dto, null);

        assertEquals("a@b.it", created.getData().get("email"));
    }

    @Test
    void updateExcludesTheRecordItselfFromTheDuplicateCheck() {
        givenUniqueEmailField();
        UUID actorId = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(actorId);
        existing.setData(Map.of("email", "a@b.it"));
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
        // il check di duplicato deve escludere il record stesso: risalvare lo stesso valore e' lecito
        when(recordQueryService.hasDuplicateValue(objectType.getId(), "email", "a@b.it", existing.getId()))
                .thenReturn(false);

        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("email", "a@b.it"));

        Record updated = service.update(actor(actorId), "lead", existing.getId(), dto, null);

        assertEquals("a@b.it", updated.getData().get("email"));
        verify(recordQueryService).hasDuplicateValue(objectType.getId(), "email", "a@b.it", existing.getId());
    }

    // --- B1: i campi RELATION devono allineare record_link al valore corrente del campo ---

    private FieldDef field(String key, FieldType type) {
        FieldDef f = new FieldDef();
        f.setKey(key);
        f.setLabel(key);
        f.setType(type);
        return f;
    }

    private FieldDef relationField(String key) {
        return field(key, FieldType.RELATION);
    }

    private Record target(UUID id) {
        Record r = new Record();
        r.setId(id);
        r.setObjectType(objectType);
        return r;
    }

    private RecordLink link(Record source, Record target, String relationKey) {
        RecordLink l = new RecordLink();
        l.setId(UUID.randomUUID());
        l.setSource(source);
        l.setTarget(target);
        l.setRelationKey(relationKey);
        return l;
    }

    @Test
    void createWithRelationWritesTheRecordLink() {
        UUID targetId = UUID.randomUUID();
        objectType.setFields(List.of(relationField("company")));
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(targetId)).thenReturn(Optional.of(target(targetId)));
        when(recordLinkRepository.findBySource_Id(any())).thenReturn(List.of());
        when(recordLinkRepository.findBySource_IdAndTarget_IdAndRelationKey(any(), any(), any()))
                .thenReturn(Optional.empty());

        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("company", targetId.toString()));
        Record created = service.create(actor(UUID.randomUUID()), "lead", dto, null);

        var captor = org.mockito.ArgumentCaptor.forClass(RecordLink.class);
        verify(recordLinkRepository).save(captor.capture());
        assertEquals(created.getId(), captor.getValue().getSource().getId());
        assertEquals(targetId, captor.getValue().getTarget().getId());
        // convenzione "<objectKey>.<fieldKey>", la stessa dei link creati dall'azione create_link
        assertEquals("lead.company", captor.getValue().getRelationKey());
    }

    @Test
    void updateChangingTheTargetReplacesTheLink() {
        UUID oldTargetId = UUID.randomUUID();
        UUID newTargetId = UUID.randomUUID();
        objectType.setFields(List.of(relationField("company")));

        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setData(Map.of("company", oldTargetId.toString()));
        RecordLink oldLink = link(existing, target(oldTargetId), "lead.company");

        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
        when(recordRepository.findByIdAndIsDeletedFalse(newTargetId)).thenReturn(Optional.of(target(newTargetId)));
        when(recordLinkRepository.findBySource_Id(existing.getId())).thenReturn(List.of(oldLink));
        when(recordLinkRepository.findBySource_IdAndTarget_IdAndRelationKey(any(), any(), any()))
                .thenReturn(Optional.empty());

        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("company", newTargetId.toString()));
        service.update(actor(UUID.randomUUID()), "lead", existing.getId(), dto, null);

        verify(recordLinkRepository).delete(oldLink);
        var captor = org.mockito.ArgumentCaptor.forClass(RecordLink.class);
        verify(recordLinkRepository).save(captor.capture());
        assertEquals(newTargetId, captor.getValue().getTarget().getId());
    }

    @Test
    void updateClearingTheRelationRemovesTheLink() {
        UUID oldTargetId = UUID.randomUUID();
        objectType.setFields(List.of(relationField("company")));

        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setData(Map.of("company", oldTargetId.toString()));
        RecordLink oldLink = link(existing, target(oldTargetId), "lead.company");

        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));
        when(recordLinkRepository.findBySource_Id(existing.getId())).thenReturn(List.of(oldLink));

        // il validator scarta i valori vuoti, quindi la chiave sparisce da data e il campo e' svuotato
        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(Map.of("company", ""));
        service.update(actor(UUID.randomUUID()), "lead", existing.getId(), dto, null);

        verify(recordLinkRepository).delete(oldLink);
        verify(recordLinkRepository, never()).save(any(RecordLink.class));
    }


    @Test
    void detailReadsOnlyLinksTowardsRecordsNotInTheTrash() {
        UUID id = UUID.randomUUID();
        Record record = new Record();
        record.setId(id);
        record.setObjectType(objectType);
        record.setData(Map.of());
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(record));
        when(recordLinkRepository.findBySource_IdAndTarget_IsDeletedFalse(id)).thenReturn(List.of());
        when(recordLinkRepository.findByTarget_IdAndSource_IsDeletedFalse(id)).thenReturn(List.of());

        service.findOne(actor(UUID.randomUUID()), "lead", id);

        // se qualcuno tornasse ai finder non filtrati, un record cestinato ricomparirebbe fra i
        // collegamenti di chi lo punta: questo test e' la guardia contro quella regressione
        verify(recordLinkRepository, never()).findBySource_Id(id);
        verify(recordLinkRepository, never()).findByTarget_Id(id);
    }

    @Test
    void removeSoftDeletesWithoutTouchingTheLinks() {
        UUID actorId = UUID.randomUUID();
        Record existing = new Record();
        existing.setId(UUID.randomUUID());
        existing.setObjectType(objectType);
        existing.setOwnerId(actorId);
        existing.setData(Map.of());
        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        when(recordRepository.findByIdAndIsDeletedFalse(existing.getId())).thenReturn(Optional.of(existing));

        service.remove(actor(actorId), "lead", existing.getId(), null);

        assertTrue(existing.isDeleted());
        // i link restano a DB: il ripristino dal cestino deve ritrovarli intatti
        verify(recordLinkRepository, never()).delete(any(RecordLink.class));
        verify(recordLinkRepository, never()).deleteAll(any());
    }
}
