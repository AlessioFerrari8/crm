package it.northleap.backend.services;

import it.northleap.backend.entities.FieldDef;
import it.northleap.backend.entities.FieldType;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.entities.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

// risolve gli UUID dei campi RELATION nel titolo del record puntato
// Sta qui e non nel frontend, perché la lista non ha i link caricati: senza, la prima schermata
// che vede chiunque mostrerebbe UUID
@Service
@RequiredArgsConstructor
public class RelationTitleService {

    private final RecordRepository recordRepository;

    // una query per pagina, non una per riga. Si raccolgono tutti gli id di tutti i record e si fa
    // un findAllById solo. Con 50 record e 3 campi RELATION, la differenza è 1 query contro 150
    public Map<String, String> titlesFor(ObjectType objectType, List<Record> records) {
        List<String> relationKeys = objectType.getFields().stream()
                .filter(f -> f.getType() == FieldType.RELATION)
                .map(FieldDef::getKey)
                .toList();

        if (relationKeys.isEmpty() || records.isEmpty()) return Map.of();

        Set<UUID> ids = new HashSet<>();
        for (Record r : records) {
            if (r.getData() == null) continue;
            for (String key : relationKeys) {
                collectIds(r.getData().get(key), ids);
            }
        }
        return titlesOf(ids);
    }

    // usata dal pdf, che ha un valore solo in mano e non un oggetto intero
    public Map<String, String> titlesOf(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<String, String> out = new HashMap<>();
        for (Record r : recordRepository.findAllById(ids)) {
            // un record cestinato resta collegato ma non si mostra: fuori dalla mappa, e chi legge
            // cade sull'id accorciato
            if (!r.isDeleted()) {
                out.put(r.getId().toString(), r.getTitle() == null ? "" : r.getTitle());
            }
        }
        return out;
    }

    // il valore nel jsonb è un UUID o una lista di UUID se è config.multiple
    // Quello che non è un UUID non è un errore: è un dato vecchio, si ignora
    public void collectIds(Object value, Set<UUID> into) {
        if (value == null) return;
        if (value instanceof Collection<?> c) {
            c.forEach(v -> collectIds(v, into));
            return;
        }
        try {
            into.add(UUID.fromString(String.valueOf(value)));
        } catch (IllegalArgumentException ignored) {
            // non era un UUID
        }
    }
}
