package it.northleap.backend.portal;


import it.northleap.backend.entities.FileObject;
import it.northleap.backend.services.FileStorageService;
import it.northleap.backend.services.PdfService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// tutte le rotte del cliente. Il principal lo mette PortalAuthenticationFilter; senza, 401. Un token interno qui non vale
// niente: il filtro interno popola un Actor, e questo controller non lo legge mai
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalAuthService auth;
    private final PortalService portal;
    private final PortalTokenService tokens;
    private final FileStorageService fileStorageService;

    public record Login(String email, String password) { }
    public record Accept(String token, String password) { }
    public record NewTicket(String subject, String description) { }
    public record Message(String body) { }
    public record ChangePassword(String currentPassword, String newPassword) { }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Login body) {
        PortalUser u = auth.login(body.email(), body.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, tokens.cookie(tokens.issue(u)).toString())
                .body(Map.of("email", u.getEmail()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.clear().toString()).build();
    }

    @PostMapping("/auth/accept-invite")
    public ResponseEntity<Void> acceptInvite(@RequestBody Accept body) {
        auth.acceptInvite(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password")
    public ResponseEntity<Void> password(HttpServletRequest req, @RequestBody ChangePassword body) {
        auth.changePassword(me(req), body.currentPassword(), body.newPassword());
        // la sessione corrente muore con le altre: si rientra con la password nuova
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.clear().toString()).build();
    }

    @GetMapping("/me")
    public Map<String, String> me(HttpServletRequest req, @RequestParam(required = false) String ignored) {
        return Map.of("email", me(req).email());
    }

    @GetMapping("/tickets")
    public List<PortalService.TicketView> tickets(HttpServletRequest req) { return portal.tickets(me(req)); }

    @GetMapping("/tickets/{id}")
    public PortalService.TicketDetail ticket(HttpServletRequest req, @PathVariable UUID id) { return portal.ticket(me(req), id); }

    @PostMapping("/tickets")
    public PortalService.TicketView open(HttpServletRequest req, @RequestBody NewTicket body) {
        return portal.openTicket(me(req), body.subject(), body.description(), req.getRemoteAddr());
    }

    @PostMapping("/tickets/{id}/messages")
    public PortalService.MessageView reply(HttpServletRequest req, @PathVariable UUID id, @RequestBody Message body) {
        return portal.reply(me(req), id, body.body());
    }

    @GetMapping("/documents")
    public List<PortalService.DocumentView> documents(HttpServletRequest req) { return portal.documents(me(req)); }

    @GetMapping("/documents/{id}/pdf")
    public ResponseEntity<byte[]> pdf(HttpServletRequest req, @PathVariable UUID id) {
        PdfService.Rendered r = portal.pdf(me(req), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(r.filename(), StandardCharsets.UTF_8).build().toString())
                .body(r.bytes());
    }

    @PostMapping("/quotes/{id}/accept")
    public ResponseEntity<Void> accept(HttpServletRequest req, @PathVariable UUID id, @RequestBody(required = false) Message note) {
        portal.acceptQuote(me(req), id, note == null ? null : note.body(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> file(HttpServletRequest req, @PathVariable UUID id) {
        FileObject f = portal.file(me(req), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM) // mai il mime dichiarato dall'upload: è input utente
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(fileStorageService.resolveOnDisk(f)));
    }

    private PortalPrincipal me(HttpServletRequest req) {
        Object p = req.getAttribute(PortalPrincipal.REQUEST_ATTRIBUTE);
        if (p instanceof PortalPrincipal pp) return pp;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Accedi all'area clienti");
    }

}
