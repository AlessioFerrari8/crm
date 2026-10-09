package it.northleap.backend.controllers;


import it.northleap.backend.dtos.NpsStats;
import it.northleap.backend.entities.NpsSurvey;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.NpsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// resouce "campaign": l'invito NPS è una comunicazione verso i contatti, stesso livello
@RestController
@RequiredArgsConstructor
@Validated
public class NpsController {

    private final NpsService npsService;

    public record Answer(@Min(0) @Max(10) int score, @Size(max = 2000) String comment) { }

    @GetMapping("/api/nps")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public List<NpsSurvey> list() { return npsService.list(); }

    @PostMapping("/api/nps")
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public NpsSurvey create(@RequestBody NpsSurvey body) { return npsService.save(null, body); }

    @PatchMapping("/api/nps/{id}")
    @RequirePerm(resource = "campaign", action = PermAction.WRITE)
    public NpsSurvey update(@PathVariable UUID id, @RequestBody NpsSurvey body) { return npsService.save(id, body); }

    @GetMapping("/api/nps/{id}/stats")
    @RequirePerm(resource = "campaign", action = PermAction.READ)
    public NpsStats stats(@PathVariable UUID id) { return npsService.stats(id); }

    // invio a mano (trigger MANUAL): gli altri partono dal workflow, azione create_nps_resource
    @PostMapping("/api/nps/{id}/invite")
    @RequirePerm(resource = "campaign", action = PermAction.EXECUTE)
    public Map<String, Long> invite(@PathVariable UUID id, @RequestBody List<UUID> contactIds) {
        long sent = contactIds.stream().limit(500).filter(c -> npsService.invite(id, c).isPresent()).count();
        return Map.of("sent", sent, "skipped", contactIds.size() - sent);
    }

    // pubblici, sotto /api/public/: una riga di SecurityConfig è già fuori dal CSRF
    @GetMapping("/api/publics/nps/{token}")
    public NpsService.PublicQuestion question(@PathVariable String token) { return npsService.question(token); }

    @PostMapping("/api/public/nps/{token}")
    public ResponseEntity<Void> answer(@PathVariable String token, @RequestBody @Validated Answer a, HttpServletRequest req) {
        npsService.answer(token, a.score(), a.comment(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
