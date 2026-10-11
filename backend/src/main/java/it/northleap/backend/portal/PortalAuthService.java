package it.northleap.backend.portal;


import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.services.EmailService;
import it.northleap.backend.services.HashUtil;
import it.northleap.backend.services.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

// il flusso di UserService.invite riscritto, non condiviso: due metodi simili sono meglio di uno che decide con un
// booleano se il risultato è un dipendente o un estraneo
@Service
@RequiredArgsConstructor
public class PortalAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PortalUserRepository users;
    private final RecordRepository recordRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public record InviteResult(UUID portalUserId, String link) { }

    // lato interno: invita un contatto. Il link torna anche all'admin, che lo può mandare a mano se le email sono spente
    @Transactional
    public InviteResult invite(UUID contactId) {
        Record contact = recordRepository.findByIdAndIsDeletedFalse(contactId)
                .filter(r -> "contact".equals(r.getObjectType().getKey()))
                .orElseThrow(() -> new NotFoundException("Contatto non trovato"));
        Object e = contact.getData().get("email");
        if (e == null || !e.toString().contains("@")) throw new BadRequestException("Il contatto non ha un'email");
        String email = e.toString().trim().toLowerCase();

        PortalUser u = users.findByEmailIgnoreCase(email).orElseGet(PortalUser::new);
        if (u.getId() != null && !u.getContactRecordId().equals(contactId)) {
            throw new BadRequestException("Questa email è già usata da un altro contatto del portale");
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        u.setEmail(email);
        u.setContactRecordId(contactId);
        u.setActive(true);
        u.setInviteTokenHash(HashUtil.sha256Hex(token));
        u.setInviteExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        users.save(u);

        String link = frontendBaseUrl + "/portal/invite?token=" + token;
        emailService.send(email, "Il tuo accesso all'area clienti", "<p>Per accedere all'area clienti scegli una password da qui:</p><p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>Il link vale 7 giorni.</p>");
        return new InviteResult(u.getId(), link);
    }

    @Transactional
    public void acceptInvite(String token, String password) {
        PortalUser u = users.findByInviteTokenHash(HashUtil.sha256Hex(token))
                .filter(x -> x.getInviteExpiresAt() != null && x.getInviteExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Invito non valido o scaduto", "INVITE_INVALID"));
        PasswordPolicy.assertValid(password);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setInviteTokenHash(null);
        u.setInviteExpiresAt(null);
        u.setTokensValidAfter(Instant.now());
    }

    // stesso messaggio per email sconosciuta e password sbagliata: la differenza dice chi è cliente
    @Transactional
    public PortalUser login(String email, String password) {
        PortalUser u = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(PortalUser::isActive)
                .filter(x -> x.getPasswordHash() != null && passwordEncoder.matches(password, x.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali non valide"));
        u.setLastLoginAt(Instant.now());
        return u;
    }

    // cambio password = tutte le sessioni aperte muoiono (tokensValidAfter)
    @Transactional
    public void changePassword(PortalPrincipal p, String current, String next) {
        PortalUser u = users.findById(p.portalUserId()).orElseThrow();
        if (!passwordEncoder.matches(current, u.getPasswordHash())) {
            throw new BadRequestException("Password attuale errata", "CURRENT_PASSWORD_INVALID");
        }
        PasswordPolicy.assertValid(next);
        u.setPasswordHash(passwordEncoder.encode(next));
        u.setTokensValidAfter(Instant.now());
    }

    public List<PortalUser> list() {
        return users.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public PortalUser setActive(UUID id, boolean active) {
        PortalUser u = users.findById(id).orElseThrow(() -> new NotFoundException("Utente portale non trovato"));
        u.setActive(active);
        return u;
    }
}
