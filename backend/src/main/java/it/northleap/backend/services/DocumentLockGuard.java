package it.northleap.backend.services;


import it.northleap.backend.entities.EInvoiceStatus;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.EInvoiceRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// i documenti che non si toccano o più: la fattura trasmessa allo SDI e il rpeventivo accettato dal cliente. Si blocca
// nel service, non nella UI - l'API ed anche i workflow passano di qui senza guardare nessun bottone
@Component
@RequiredArgsConstructor
public class DocumentLockGuard {

    private final EInvoiceRepository eInvoiceRepository;
    private final RecordRepository recordRepository;

    public void assertEditable(String objectKey, UUID recordId) {
        if ("invoice".equals(objectKey) && eInvoiceRepository.existsByInvoice_IdAndStatusIn(recordId, EInvoiceStatus.LOCKING)) {
            throw new BadRequestException("Fattura già trasmessa allo SDI: per correggerla emetti una nota di credito", "INVOICE_LOCKED");
        }
        if ("quote".equals(objectKey) && recordRepository.findByIdAndIsDeletedFalse(recordId)
                .map(r -> r.getData() != null && r.getData().get("acceptedAt") != null).orElse(false)) {
            // il pdf che il cliente ha in mano e quello nel CRM devono dire la stessa cosa
            throw new BadRequestException("Preventivo già accettato dal cliente: fai una nuova revisione", "QUOTE_ACCEPTED");
        }
    }
}
