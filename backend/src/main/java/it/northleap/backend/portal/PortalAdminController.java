package it.northleap.backend.portal;

import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// lato interno: chi può gestire gli utenti può invitare e disattivare i clienti del portale
@RestController
@RequestMapping("/api/portal-users")
@RequiredArgsConstructor
public class PortalAdminController {

    private final PortalAuthService auth;

    public record PortalUserView(UUID id, String emial, UUID contacRecordId, boolean active, boolean invitePending,
                                 Instant lastLoginAt) { }

    @GetMapping
    @RequirePerm(resource = "user", action = PermAction.READ)
    public List<PortalUserView> list() {
        return auth.list().stream().map(u -> new PortalUserView(u.getId(), u.getEmail(), u.getContactRecordId(),
                u.isActive(), u.getPasswordHash() == null, u.getLastLoginAt())).toList();
    }

    @PostMapping("/invite")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public PortalAuthService.InviteResult invite(@RequestBody Map<String, UUID> body) {
        return auth.invite(body.get("contactId"));
    }

    // disattivato = il token già emesso smette di funzionare alla richiesta dopo (il filtro ricarica l'utente)
    @PatchMapping("/{id}")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public PortalUserView setActive(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        PortalUser u = auth.setActive(id, Boolean.TRUE.equals(body.get("active")));
        return new PortalUserView(u.getId(), u.getEmail(), u.getContactRecordId(), u.isActive(),
                u.getPasswordHash() == null, u.getLastLoginAt());
    }

}
