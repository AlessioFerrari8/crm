package it.northleap.backend.controllers;


import it.northleap.backend.entities.CampaignRecipient;
import it.northleap.backend.repositories.CampaignRecipientRepository;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.services.CampaignRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Optional;

// pubblici (sotto /api/public/, quindi fuori dal CSRF): pixel, click e disiscrizione
@RestController
@RequestMapping("/api/public/c/{token}")
@RequiredArgsConstructor
public class CampaignTrackingController {

    // GIF 1X1 trasparente, 43 byte costanti
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final CampaignRecipientRepository recipientRepository;
    private final RecordRepository recordRepository;

    // sempre 200, anche col token che non esiste: un 404 direbbe a chi prova quali token sono validi
    @GetMapping("/o.gif")
    @Transactional
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        recipientRepository.findByToken(token).ifPresent(r -> {
            if (r.getOpenedAt() == null) r.setOpenedAt(Instant.now());
        });
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(PIXEL);
    }

    // la firma non è opzionale: senza, ?u=http://sito-cattivo fa del dominio un open redirect
    @GetMapping("/l")
    @Transactional
    public ResponseEntity<Void> click(@PathVariable String token, @RequestParam("u") String url, @RequestParam("s") String sig) {
        Optional<CampaignRecipient> r = recipientRepository.findByToken(token);
        if (r.isEmpty() || !(url.startsWith("https://") || url.startsWith("http://"))) {
            return ResponseEntity.badRequest().build();
        }
        String expected = CampaignRenderer.sign(r.get().getCampaign(), url);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.badRequest().build();
        }
        if (r.get().getClickedAt() == null) r.get().setClickedAt(Instant.now());
        // un click è anche un'apertura: con le immagini bloccate il pixel non parte mai
        if (r.get().getOpenedAt() == null) r.get().setOpenedAt(Instant.now());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    // get mostra la conferma, post disisccrive. Un get che modifica verrebbe eseguito dagli antivirus che aprono i
    // link delle email per controllarli
    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public String confirm(@PathVariable String token) {
        return page("Vuoi davvero smettere di ricevere le nostre email?",
                "<form method=\\\"post\\\"><button type=\\\"submit\\\">Si', disiscrivimi</button></form>");
    }

    // anche il post one-click di List-Unsubscribe-Post arriva qui
    @PostMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public String unsubscribe(@PathVariable String token) {
        recipientRepository.findByToken(token).ifPresent(r -> {
            if (r.getRecordId() == null) return;
            // l'opt-out sta sul contatto: vale per tute le campagne future, non solo per questa
            recordRepository.findByIdAndIsDeletedFalse(r.getRecordId()).ifPresent(rec -> {
                var data = new HashMap<>(rec.getData());
                data.put("marketingOptOut", true);
                data.put("marketingOptOutAt", Instant.now().toString());
                rec.setData(data);
            });
        });
        return page("Fatto.", "Non riceverai più comunicazioni commerciali da noi.");
    }

    private String page(String title, String body) {
        return "<!doctype html><html lang=\"it\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<body style=\"font-family:system-ui;max-width:32rem;margin:4rem auto;padding:0 1rem\"><h1>"
                + HtmlUtils.htmlEscape(title) + "</h1><p>" + body + "</p></body></html>";
    }

}
