package it.northleap.backend.portal;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;


// il metodo che decide cosa esiste per un cliente. Ogni caso che deve dare 404 è qui
@ExtendWith(MockitoExtension.class)
class PortalAccessServiceTest {

    @Mock private RecordRepository recordRepository;
    @Mock private ObjectTypeRepository objectTypeRepository;

    private PortalAccessService access;
    private final UUID contactId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private PortalPrincipal me;

    @BeforeEach
    void setUp() {
        access = new PortalAccessService(recordRepository, objectTypeRepository);
        me = new PortalPrincipal(UUID.randomUUID(), contactId, "cliente@example.com");
        Record contact = record(contactId, "contact", Map.of("company", companyId.toString()));
        lenient().when(recordRepository.findByIdAndIsDeletedFalse(contactId)).thenReturn(Optional.of(contact));
    }

    @Test
    void ticketOfTheContactIsVisible() {
        Record t = stored("ticket", Map.of("contact", contactId.toString()));
        assertThat(access.assertOwned(me, t.getId(), PortalAccessService.TICKET)).isSameAs(t);
    }

    @Test
    void documentOfTheCompanyIsVisible() {
        Record q = stored("quote", Map.of("company", companyId.toString()));
        assertThat(access.assertOwned(me, q.getId(), PortalAccessService.DOCUMENTS)).isSameAs(q);
    }

    @Test
    void recordOfAnotherCustomerIs404NotForbidden() {
        Record other = stored("ticket", Map.of("contact", UUID.randomUUID().toString(), "company", UUID.randomUUID().toString()));
        assertThatThrownBy(() -> access.assertOwned(me, other.getId(), PortalAccessService.TICKET))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownedRecordOfATypeNotAllowedIs404() {
        // la nota interna collegata al contatto non e' un tipo del portale
        Record opp = stored("opportunity", Map.of("contact", contactId.toString()));
        assertThatThrownBy(() -> access.assertOwned(me, opp.getId(), PortalAccessService.DOCUMENTS))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void multipleRelationsAreMatchedToo() {
        Record t = stored("ticket", Map.of("contact", List.of(UUID.randomUUID().toString(), contactId.toString())));
        assertThat(access.assertOwned(me, t.getId(), PortalAccessService.TICKET)).isSameAs(t);
    }

    private Record stored(String key, Map<String, Object> data) {
        Record r = record(UUID.randomUUID(), key, data);
        when(recordRepository.findByIdAndIsDeletedFalse(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private Record record(UUID id, String key, Map<String, Object> data) {
        ObjectType t = new ObjectType();
        t.setKey(key);
        Record r = new Record();
        r.setId(id);
        r.setObjectType(t);
        r.setData(new HashMap<>(data));
        return r;
    }
}
