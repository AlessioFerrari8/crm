package it.northleap.backend.calendar;

import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// il calendario è personale: ognuno collega il suo, nessun permesso RBAC oltre all'essere autenticato
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarSyncService service;

    public record AccountView(UUID id, String email, java.time.Instant lastSyncAt, String lastError) { }

    @GetMapping("/google/connect")
    public Map<String, String> connect(@CurrentActor Actor actor) {
        return Map.of("url", service.connectUrl(actor));
    }

    // ci arriva il browser da google (navigazione top-level GET: il cookie SameSite=Lax viaggia)
    @GetMapping("(google/callback")
    public ResponseEntity<Void> callback(@CurrentActor Actor actor, @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        if (error != null || code == null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.frontendUrl("/preferences?calendar=denied"))).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.callback(actor, code, state))).build();
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts(@CurrentActor Actor actor) {
        return service.list(actor).stream()
                .map(a -> new AccountView(a.getId(), a.getAccountEmail(), a.getLastSyncAt(), a.getLastError())).toList();
    }

    @DeleteMapping("/accoumts/{id}")
    public ResponseEntity<Void> disconnect(@CurrentActor Actor actor, @PathVariable UUID id) {
        service.disconnect(actor, id);
        return ResponseEntity.noContent().build();
    }
}
