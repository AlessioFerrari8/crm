package it.northleap.backend.controllers;

import it.northleap.backend.services.CalcomWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Webhook inbound di Cal.com. Pubblico (permitAll + esente CSRF in SecurityConfig): il chiamante
// e' Cal.com, non un browser; la protezione e' la firma HMAC sul body raw, verificata nel service.
// Il body arriva come String NON deserializzata apposta: la firma va calcolata sugli stessi byte.
@RestController
@RequestMapping("/api/integrations/calcom")
@RequiredArgsConstructor
public class CalcomWebhookController {

    private final CalcomWebhookService calcomWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "X-Cal-Signature-256", required = false) String signature,
            @RequestBody String rawBody
    ) {
        return ResponseEntity.ok(calcomWebhookService.receive(signature, rawBody));
    }
}
