package it.northleap.backend.controllers;

import it.northleap.backend.dtos.CampaignAudience;
import it.northleap.backend.dtos.CampaignDto;
import it.northleap.backend.dtos.CampaignStats;
import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.CampaignRecipient;
import it.northleap.backend.entities.RecipientStatus;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.CampaignService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public List<Campaign> list() { return campaignService.list(); }

    @GetMapping("/{id}")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public Campaign get(@PathVariable UUID id) { return campaignService.get(id); }

    @PostMapping
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public Campaign create(@CurrentActor Actor actor, @Valid @RequestBody CampaignDto dto) {
        return campaignService.create(actor, dto);
    }

    @PatchMapping("/{id}")
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public Campaign update(@PathVariable UUID id, @Valid @RequestBody CampaignDto dto) {
        return campaignService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview-recipients")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public CampaignAudience audience(@CurrentActor Actor actor, @PathVariable UUID id) {
        return campaignService.audience(actor, id);
    }

    @PostMapping("/{id}/test")
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public ResponseEntity<Void> test(@CurrentActor Actor actor, @PathVariable UUID id) {
        campaignService.test(actor, id);
        return ResponseEntity.noContent().build();
    }

    // EXECUTE, non WRITE: mandare mille email è un'azione alquanto distruttiva, praticamnete quanto una cancellazione
    @PostMapping("/{id}/send")
    @RequirePerm(resource = "campaign", action = PermAction.EXECUTE)
    public Campaign send(@CurrentActor Actor actor, @PathVariable UUID id, @RequestBody Map<String, Long> body, HttpServletRequest request) {
        return campaignService.send(actor, id, body.getOrDefault("confirmCount", -1L), request.getRemoteAddr());
    }

    @PostMapping("/{id}/pause")
    @RequirePerm(resource = "campaign", action = PermAction.EXECUTE)
    public Campaign pause(@PathVariable UUID id) { return campaignService.pause(id); }

    @GetMapping("/{id}/stats")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public CampaignStats stats(@PathVariable UUID id) { return campaignService.stats(id); }

    @GetMapping("/{id}/recipients")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public List<CampaignRecipient> recipients(@PathVariable UUID id, @RequestParam(required = false)RecipientStatus status) {
        return campaignService.recipients(id, status);
    }

}
