package it.northleap.backend.services;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordTag;
import it.northleap.backend.entities.Tag;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.RecordTagRepository;
import it.northleap.backend.repositories.TagRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.entities.PermScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock private TagRepository tagRepository;
    @Mock private RecordTagRepository recordTagRepository;
    @Mock private RecordRepository recordRepository;
    @Mock private RbacService rbacService;

    @InjectMocks private TagService tagService;

    private final UUID recordId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();
    private final Actor actor = new Actor(UUID.randomUUID(), ActorType.USER, "t@t.it", List.of());

    private it.northleap.backend.entities.Record record;
    private Tag tag;

    @BeforeEach
    void setUp() {
        ObjectType type = new ObjectType();
        type.setKey("ticket");

        record = new it.northleap.backend.entities.Record();
        record.setId(recordId);
        record.setObjectType(type);

        tag = new Tag();
        tag.setId(tagId);
        tag.setName("urgente");

        when(rbacService.resolve(any(), any(), any())).thenReturn(new RbacService.Resolution(true, PermScope.ALL));
    }

    @Test
    void assegnare_due_volte_lo_stesso_tag_non_crea_due_righe() {
        RecordTag existing = new RecordTag();
        existing.setRecord(record);
        existing.setTag(tag);

        when(recordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));
        when(recordTagRepository.findByRecordId(recordId)).thenReturn(List.of(existing));

        tagService.attach(actor, recordId, tagId);

        // niente eccezione e niente riga nuova: è idempotente perché la UI lo chiama a ogni click
        verify(recordTagRepository, never()).save(any());
    }

    @Test
    void cancellare_un_tag_cancella_anche_le_assegnazioni() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(tag));

        tagService.remove(actor, tagId);

        // l'ordine conta: prima le assegnazioni, poi il tag
        var inOrder = inOrder(recordTagRepository, tagRepository);
        inOrder.verify(recordTagRepository).deleteByTagId(tagId);
        inOrder.verify(tagRepository).delete(tag);
    }
}
