package it.northleap.backend.controllers;

import it.northleap.backend.security.Actor;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.PdfService;
import it.northleap.backend.services.QuoteAcceptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class QuoteAcceptController {

    private final QuoteAcceptService service;

    @PostMapping("/api/quotes/{id}/accept-link")
    @RequirePerm(resource = "quote", action = PermAction.WRITE)
    public Map<String, String> link(@CurrentActor Actor actor, @PathVariable UUID id) {
        return Map.of("url", service.createLink(actor, id));
    }

    // pubblici: permitAll SOLO su /api/public/quote/**, rate limit basso (RateLimitFilter)
    @GetMapping("/api/public/quote/{id}")
    public QuoteAcceptService.PublicQuote view(@PathVariable UUID id, @RequestParam("t") String t, @RequestParam("s") String s) {
        return service.view(id, t, s);
    }

    @GetMapping("/api/public/quote/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam("t") String t, @RequestParam("s") String s) {
        PdfService.Rendered r = service.pdf(id, t, s);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.filename() + "\"")
                .body(r.bytes());
    }

    @PostMapping("/api/public/quote/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID id, @RequestParam("t") String t, @RequestParam("s") String s,
                                       @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        service.accept(id, t, s, body == null ? null : body.get("note"), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
