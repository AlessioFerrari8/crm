package it.northleap.backend.controllers;

import it.northleap.backend.dtos.MailboxDto;
import it.northleap.backend.dtos.UpsertMailboxDto;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.MailboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// resource "apiKey": è configurazione di sistema con dentro un segreto, quindi lo stesso livello delle API
// key. Un resource nuovo richiederebbe PermissionSeeder aggiornato, o la pagina risponde 403 a tutti, owner
// compreso (FIXED_RESOURCES si trova in PermissionSeeder)
@RestController
@RequestMapping("/api/mailboxes")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService mailboxService;

    @GetMapping
    @RequirePerm(resource = "apikey", action = PermAction.READ)
    public ResponseEntity<List<MailboxDto>> list() {
        return ResponseEntity.ok(mailboxService.list());
    }

    @PostMapping
    @RequirePerm(resource = "apikey", action = PermAction.WRITE)
    public ResponseEntity<MailboxDto> create(@Valid @RequestBody UpsertMailboxDto dto) {
        return ResponseEntity.ok(mailboxService.create(dto));
    }

    @PatchMapping("/{id}")
    @RequirePerm(resource = "apikey", action = PermAction.WRITE)
    public ResponseEntity<MailboxDto> update(@PathVariable UUID id, @Valid @RequestBody UpsertMailboxDto dto) {
        return ResponseEntity.ok(mailboxService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(resource = "apikey", action = PermAction.WRITE)
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        mailboxService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @RequirePerm(resource = "apikey", action = PermAction.WRITE)
    public ResponseEntity<Map<String, String>> test(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("message", mailboxService.test(id)));
    }

    @PostMapping("/{id}/scan")
    @RequirePerm(resource = "apikey", action = PermAction.EXECUTE)
    public ResponseEntity<Void> scan(@PathVariable UUID id) {
        mailboxService.scanNow(id);
        return ResponseEntity.noContent().build();
    }
}
