package it.northleap.backend.repositories;

import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findAllByOrderByCreatedAtDesc();
    List<Campaign> findByStatus(CampaignStatus status);
}
