package it.northleap.backend.repositories;

import it.northleap.backend.entities.CampaignRecipient;
import it.northleap.backend.entities.RecipientStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {

    Optional<CampaignRecipient> findByToken(String token);

    // solo gli id: ogni invio si ricarica la riga nella sua transazione corta
    @Query("select r.id from CampaignRecipient r where r.campaign.id = :campaignId and r.status = 'PENDING' order by r.id")
    List<UUID> pendingIds(@Param("campaignId") UUID campaignId, Pageable page);

    long countByCampaign_IdAndStatus(UUID campaignId, RecipientStatus status);

    long countByCampaign_IdAndOpenedAtIsNotNull(UUID campaignId);

    long countByCampaign_IdAndClickedAtIsNotNull(UUID campaignId);

    List<CampaignRecipient> findTop500ByCampaign_IdAndStatusOrderByEmail(UUID campaignId, RecipientStatus status);

    List<CampaignRecipient> findTop500ByCampaign_IdOrderByEmail(UUID campaignId);

    // GDPR 8.4: il contatto sparisce, la riga resta per le statistiche ma non dice più chi era
    @Modifying
    @Query("update CampaignRecipient r set r.email = null, r.token = null, r.recordId = null where r.recordId = :recordId")
    int anonymize(@Param("recordId") UUID recordId);

}
