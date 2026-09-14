package it.northleap.backend.services;


// Genera i numeri documento. Formati prodotti:
// prefisso "" + anno + padding 0 -> "2025-47" (preventivi, ordini)
// prefisso "TT" niente anno -> "TT643" (ticket)
// prefisso "FT" + anno + padding 4 -> "FT2025-0001" (fatture)
import it.northleap.backend.entities.DocumentSequence;
import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.repositories.DocumentSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private final DocumentSequenceRepository documentSequenceRepository;

    // REQUIRES_NEW: il contatore dev'essere immune al rollback di chi lo chiama, altrimenti due
    // creazioni simultanee prendono lo stesso numero. Il prezzo e' che il numero si consuma anche
    // se la create() fallisce dopo, cioe' la numerazione puo' avere dei BUCHI.
    // NON "CORREGGERE" QUESTO, e' voluto: su preventivi, ordini e DDT i buchi sono ammessi.
    // Le FATTURE no - il fisco le vuole senza buchi - ma la soluzione non e' togliere
    // REQUIRES_NEW: e' assegnare il numero al momento dell'EMISSIONE (quando la fattura diventa
    // immutabile) e dentro quella stessa transazione. Lo fara' la fatturazione elettronica.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(ObjectType objectType) {
        if (objectType.getNumberingField() == null) return null;

        int year = objectType.isNumberingUseYear() ? LocalDate.now().getYear() : 0;

        // NOTA: l'orElseGet crea la riga FUORI dal lock, perché il PESSIMISTIC_WRITE non può
        // bloccare una riga che non esiste ancora. Due creazioni simultanee del primissimo
        // documento dell'anno inseriscono due righe e una delle due viola l'UNIQUE
        // (object_key, year). Corsa strettissima e solo sul primo documento dell'anno:
        // per chiuderla servirebbe un retry in una transazione nuova, oppure pre-creare le
        // righe nel seeder
        DocumentSequence seq = documentSequenceRepository
                .findByObjectKeyAndYear(objectType.getKey(), year)
                .orElseGet(() -> {
                    DocumentSequence created = new DocumentSequence();
                    created.setObjectKey(objectType.getKey());
                    created.setYear(year);
                    created.setCounter(0);
                    return created;
                });

        seq.setCounter(seq.getCounter() + 1);
        documentSequenceRepository.save(seq);

        return format(objectType, year, seq.getCounter());
    }

    private String format(ObjectType type, int year, long counter) {
        String number = type.getNumberingPadding() > 0
                ? String.format("%0" + type.getNumberingPadding() + "d", counter)
                : String.valueOf(counter);

        StringBuilder sb = new StringBuilder();
        if (type.getNumberingPrefix() != null) sb.append(type.getNumberingPrefix());
        if (year > 0) sb.append(year).append(type.getNumberingSeparator());
        sb.append(number);
        return sb.toString();
    }


}
