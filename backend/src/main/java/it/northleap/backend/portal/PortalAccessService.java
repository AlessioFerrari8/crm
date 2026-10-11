package it.northleap.backend.portal;


import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.entities.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Il cammino di autorizzazione del portale, uno solo, in un file solo: dal contatto autenticato alla sua azienda, ai
// record dei tipi ammessi collegati all'uno o all'altra. Quello che non è raggiungibile da un Actor con dei ruoli, e un cliente
// con un ruolo è un errore di scope dal vedere tutto il database
@Service
@RequiredArgsConstructor
public class PortalAccessService {

    public static final Set<String> TICKET = Set.of("ticket");
    public static final Set<String> DOCUMENTS = Set.of("quote", "sales_order", "delivery_note", "invoice");

    private final RecordRepository recordRepository;
    private final ObjectTypeRepository objectTypeRepository;

    public List<Record> owned(PortalPrincipal p, Set<String> keys) {
        List<UUID> types = objectTypeRepository.findAll().stream()
                .filter(t -> keys.contains(t.getKey())).map(ObjectType::getId).toList();
        if (types.isEmpty()) return List.of();
        UUID company = companyOf(p);
        return recordRepository.findForPortal(types, p.contactId().toString(), company == null ? null : company.toString());
    }

    // ogni get per id passa di qui. 404 e 403: un 403 confermerebbe che il record esiste
    public Record assertOwned(PortalPrincipal p, UUID recordId, Set<String> keys) {
        Record r = recordRepository.findByIdAndIsDeletedFalse(recordId)
                .filter(x -> keys.contains(x.getObjectType().getKey()))
                .orElseThrow(() -> new NotFoundException("Non trovato"));
        UUID company = companyOf(p);
        if (links(r, "contact", p.contactId()) || (company != null && links(r, "company", company))) return r;
        throw new NotFoundException("Non trovato");
    }

    public UUID companyOf(PortalPrincipal p) {
        Record contact = recordRepository.findByIdAndIsDeletedFalse(p.contactId())
                .orElseThrow(() -> new NotFoundException("Contatto non trovato"));
        Object v = contact.getData() == null ? null : contact.getData().get("company");
        if (v instanceof Collection<?> c) v = c.isEmpty() ? null : c.iterator().next();
        return v == null || v.toString().isBlank() ? null : UUID.fromString(v.toString());
    }

    private boolean links(Record r, String key, UUID target) {
        Object v = r.getData() == null ? null : r.getData().get(key);
        if (v instanceof Collection<?> c) return c.stream().anyMatch(x -> target.toString().equals(String.valueOf(x)));
        return v != null && target.toString().equals(v.toString());
    }

}
