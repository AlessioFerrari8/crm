package it.northleap.backend.services;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.RecordLink;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.RecordLinkRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.PermAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.repositories.RecordNoteRepository;
import it.northleap.backend.repositories.RecordTagRepository;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    @Mock private RecordRepository recordRepository;
    @Mock private RecordLinkRepository recordLinkRepository;
    @Mock private RecordNoteRepository recordNoteRepository;
    @Mock private RecordTagRepository recordTagRepository;
    @Mock private RbacService rbacService;
    @Mock private AuditService auditService;
    @InjectMocks private MergeService mergeService;
    private final Actor actor = new Actor(UUID.randomUUID(), ActorType.USER, "a@b.it", List.of());
    private ObjectType type;
    private Record winner;
    private Record loser;
    private Record company;

    @BeforeEach
    void setUp() {
        type = new ObjectType();
        type.setId(UUID.randomUUID());
        lenient().when(rbacService.resolve(any(), any(), any(PermAction.class)))
                .thenReturn(new RbacService.Resolution(true, PermScope.ALL));
        winner = record(type, "Mario Rossi");
        loser = record(type, "M. Rossi");
        company = record(new ObjectType(), "Acme spa");
    }

    private Record record(ObjectType t, String title) {
        Record r = new Record();
        r.setId(UUID.randomUUID());
        r.setObjectType(t);
        r.setTitle(title);
        r.setData(new HashMap<>());
        return r;
    }

    private RecordLink link(Record source, Record target, String key) {
        RecordLink l = new RecordLink();
        l.setId(UUID.randomUUID());
        l.setSource(source);
        l.setTarget(target);
        l.setRelationKey(key);
        return l;
    }

    @Test
    void due_link_alla_stessa_azienda_diventano_uno_solo() {
        when(recordRepository.findByIdAndIsDeletedFalse(winner.getId()))
                .thenReturn(Optional.of(winner));

        when(recordRepository.findByIdAndIsDeletedFalse(loser.getId()))
                .thenReturn(Optional.of(loser));

        // il perdente e il vincitore puntano entrambi alla stessa azienda
        when(recordLinkRepository.findBySource_Id(loser.getId()))
                .thenReturn(List.of(link(loser, company, "company")));

        when(recordLinkRepository.findBySource_Id(winner.getId()))
                .thenReturn(List.of(link(winner, company, "company")));

        when(recordLinkRepository.findByTarget_Id(any())).thenReturn(List.of());

        assertThatCode(() -> mergeService.merge(actor, winner.getId(), loser.getId(), List.of()))
                .doesNotThrowAnyException();


        // il link del perdente non viene ripuntato sul vincitore (creerebbe un duplicato), quindi viene cancellato
        verify(recordLinkRepository).delete(argThat(l -> l.getSource().getId().equals(loser.getId())));
        verify(recordLinkRepository, never()).save(argThat(l -> l.getSource().getId().equals(winner.getId())
                        && l.getTarget().getId().equals(company.getId())));

    }

    @Test
    void il_dedup_guarda_anche_i_link_in_entrata() {
        // il caso simmetrico: l'azienda punta al contatto, non viceversa
        when(recordRepository.findByIdAndIsDeletedFalse(winner.getId()))
                .thenReturn(Optional.of(winner));

        when(recordRepository.findByIdAndIsDeletedFalse(loser.getId()))
                .thenReturn(Optional.of(loser));

        when(recordLinkRepository.findBySource_Id(any())).thenReturn(List.of());
        when(recordLinkRepository.findByTarget_Id(loser.getId()))
                .thenReturn(List.of(link(company, loser, "contacts")));

        when(recordLinkRepository.findByTarget_Id(winner.getId()))
                .thenReturn(List.of(link(company, winner, "contacts")));

        assertThatCode(() -> mergeService.merge(actor, winner.getId(), loser.getId(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void un_record_senza_titolo_non_fa_esplodere_niente() {
        loser.setTitle(null);
        when(recordRepository.findByIdAndIsDeletedFalse(winner.getId()))
                .thenReturn(Optional.of(winner));
        when(recordRepository.findByIdAndIsDeletedFalse(loser.getId()))
                .thenReturn(Optional.of(loser));
        when(recordLinkRepository.findBySource_Id(any())).thenReturn(List.of());
        when(recordLinkRepository.findByTarget_Id(any())).thenReturn(List.of());

        assertThatCode(() -> mergeService.merge(actor, winner.getId(), loser.getId(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void i_campi_scelti_dal_perdente_finiscono_nel_vincitore() {
        loser.getData().put("phone", "045 1234567");
        when(recordRepository.findByIdAndIsDeletedFalse(winner.getId()))
                .thenReturn(Optional.of(winner));
        when(recordRepository.findByIdAndIsDeletedFalse(loser.getId()))
                .thenReturn(Optional.of(loser));
        when(recordLinkRepository.findBySource_Id(any())).thenReturn(List.of());
        when(recordLinkRepository.findByTarget_Id(any())).thenReturn(List.of());

        // la mappa dei campi da prendere dal perdente: campo -> id sorgente
        mergeService.merge(actor, winner.getId(), loser.getId(), List.of("phone", loser.getId().toString()));

        assertThat(winner.getData()).containsEntry("phone", "045 1234567");
    }

    @Test
    void un_record_nel_cestino_non_si_puo_unire() {
        when(recordRepository.findByIdAndIsDeletedFalse(loser.getId()))
                .thenReturn(Optional.empty());
        lenient().when(recordRepository.findByIdAndIsDeletedFalse(winner.getId()))
                .thenReturn(Optional.of(winner));

        assertThatThrownBy(() -> mergeService.merge(actor, winner.getId(), loser.getId(), List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    
}
