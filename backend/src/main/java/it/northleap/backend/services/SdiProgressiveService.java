package it.northleap.backend.services;


import it.northleap.backend.entities.DocumentSequence;
import it.northleap.backend.repositories.DocumentSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// il ProgressivoInvio identifica il FILE, non la fattura: una fattura scartata si ritrasmette con lo stesso
// numero e un progressivo nuovo. Stessa tabella dei numeri documento, riga riservata: i buchi qui sono
// ammessi, quindi REQUIRES_NEW come DocumentNumberService
@Service
@RequiredArgsConstructor
public class SdiProgressiveService {

    private static final String KEY = "__sdi_transmission";

    private final DocumentSequenceRepository documentSequenceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next() {
        DocumentSequence seq = documentSequenceRepository.findByObjectKeyAndYear(KEY, 0)
                .orElseGet(() -> {
                    DocumentSequence s = new DocumentSequence();
                    s.setObjectKey(KEY);
                    s.setYear(0);
                    return s;
                });

        seq.setCounter(seq.getCounter() + 1);
        documentSequenceRepository.save(seq);
        // max 5 caratteri alfanumerici: in base 36 sono 60 milioni di file
        String p = Long.toString(seq.getCounter(), 36).toUpperCase();
        return "0".repeat(Math.max(0, 5 - p.length())) + p;
    }
}
