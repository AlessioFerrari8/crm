package it.northleap.backend.services;

import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.CampaignStatus;
import it.northleap.backend.entities.RecipientStatus;
import it.northleap.backend.repositories.CampaignRecipientRepository;
import it.northleap.backend.repositories.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

// la coda è la tabella: le righe PENDING sono i messaggi da mandare. Niente broker
@Component
@RequiredArgsConstructor
public class CampaignSender {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final CampaignDelivery delivery;

    // il tetto al minuto è la reputazione del dominio: 2000 email in un secondo da un dominio nuovo sono considerate spam
    @Value("${app.campaign.batch-per-minute:60}")
    private int batch;

    @Scheduled(fixedDelayString = "${app.campaign.delay-ms:60000}")
    public void tick() {
        int budget = batch;
        for (Campaign c : campaignRepository.findByStatus(CampaignStatus.SENDING)) {
            if (budget <= 0) return;
            var ids = recipientRepository.pendingIds(c.getId(), PageRequest.of(0, budget));
            for (UUID id : ids) delivery.deliver(id);
            budget -= ids.size();
            if (recipientRepository.countByCampaign_IdAndStatus(c.getId(), RecipientStatus.PENDING) == 0) {
                finish(c.getId());
            }
        }
    }

    private void finish(UUID id) {
        campaignRepository.findById(id).ifPresent(c -> {
            if (c.getStatus() != CampaignStatus.SENDING) return;
            c.setStatus(CampaignStatus.SENT);
            c.setFinishedAt(Instant.now());
            campaignRepository.save(c);
        });
    }
}
