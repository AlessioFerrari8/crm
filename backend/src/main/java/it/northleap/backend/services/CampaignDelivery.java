package it.northleap.backend.services;

import it.northleap.backend.entities.*;
import it.northleap.backend.entities.Record;
import it.northleap.backend.repositories.CampaignRecipientRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// un messaggio, una transazione corta e indipendente. In una classe a parte: chiamato dentro CampaignSeeder, il proxy di
// @Transactional non scatterebbe
@Service
@RequiredArgsConstructor
public class CampaignDelivery {

    private final CampaignRecipientRepository recipientRepository;
    private final RecordRepository recordRepository;
    private final CampaignRenderer renderer;
    private final EmailService emailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID recipientId) {
        CampaignRecipient r = recipientRepository.findById(recipientId).orElse(null);
        if (r == null || r.getStatus() != RecipientStatus.PENDING) return;
        Campaign c = r.getCampaign();
        if (c.getStatus() != CampaignStatus.SENDING) return; // messa in pausa fra un messaggio e l'altro

        Record record = r.getRecordId() == null ? null : recordRepository.findByIdAndIsDeletedFalse(r.getRecordId()).orElse(null);
        // regola 1: l'opt-out si controlla adesso, non quando si è costruita la lista
        if (record == null || CampaignService.optedOut(record) || r.getEmail() == null) {
            r.setStatus(RecipientStatus.SKIPPED);
            return;
        }

        try {
            CampaignRenderer.Rendered out = renderer.render(c, record, r.getToken());
            emailService.sendMessage(new MailTransport.Outgoing(r.getEmail(), c.getSubject(), out.html(), out.text(), out.headers()));
            // sent subito dopo l'ok del trasporto. Se il processo muore qui, al riavvio riparte dal successivo
            r.setStatus(RecipientStatus.SENT);
            r.setSentAt(Instant.now());
        } catch(Exception e) {
            // failed col motivo, e si va avanti
            r.setStatus(RecipientStatus.FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            r.setFailReason(msg.length() > 500 ? msg.substring(0, 500) : msg);
        }
    }
}
