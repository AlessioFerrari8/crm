package it.northleap.backend.calendar;

import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.User;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.services.HashUtil;
import it.northleap.backend.services.RecordsService;
import it.northleap.backend.services.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleCalendarClient google;
    private final CalendarAccountRepository accounts;
    private final EventMappingRepository mappings;
    private final OAuthStateRepository states;
    private final SecretCipher cipher;
    private final RecordsService recordsService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Value("${app.fontend.base-url}")
    private String frontendBaseUrl;

    // fuso orario in cui un evento tutto-il-giorno diventa "mezzanotte dui quel giorno"
    @Value("${app.calendar.zone:Europe/Rome}")
    private String zone;

    public String connectUrl(Actor actor) {
        if (!google.isConfigured()) throw new BadRequestException("Integrazione Google non configurata (GOOGLE_CLIENT_ID)");
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        OAuthState s = new OAuthState();
        s.setStateHash(HashUtil.sha256Hex(state));
        s.setUserId(actor.id());
        s.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        states.save(s);
        return google.authorizeUrl(state);
    }

    // callback: state verificato, monouso, e dello stesso utente che ha iniziato il collegamento
    @Transactional
    public String callback(Actor actor, String code, String state) {
        OAuthState s = states.findById(HashUtil.sha256Hex(state == null ? "" : state))
                .filter(x -> x.getExpiresAt().isAfter(Instant.now()) && x.getUserId().equals(actor.id))
                .orElseThrow(() -> new BadRequestException("Collegamento scaduto o non valido: riprova"));
        states.delete(s);
        states.purgeExpired(Instant.now());

        GoogleCalendarClient.Tokens t = google.exchange(code);
        if (t.refreshToken() == null) throw new BadRequestException("Google non ha restituito il refresh token: riprova");
        String email = google.primaryCalendarId(t.accessToken());
        CalendarAccount a = accounts.findByUserIdAndAccountEmail(actor.id(), email).orElseGet(CalendarAccount::new);
        a.setUserId(actor.id());
        a.setAccountEmail(email);
        a.setRefreshTokenEnc(cipher.encrypt(t.refreshToken()));
        a.setAccessTokenEnc(cipher.encrypt(t.accessToken()));
        a.setAccessTokenExpiresAt(t.expiresAt());
        a.setActive(true);
        a.setSyncToken(null); // collegamento nuovo = giro completo
        accounts.save(a);
        return frontendBaseUrl + "/preferences?calendar=connected";
    }

    public String frontendUrl(String path) {
        return frontendBaseUrl + path;
    }

    public List<CalendarAccount> list(Actor actor) {
        return accounts.findByUserId(actor.id());
    }

    // scollegare toglie anche gli eventi importanti, tutti insieme: è il motivo del campo calendarAccount
    @Transactional
    public void disconnect(Actor actor, UUID id) {
        CalendarAccount a = accounts.findById(id).filter(x -> x.getUserId().equals(actor.id()))
                .orElseThrow(() -> new NotFoundException("Calendario non trovato"));
        for (EventMapping m : mappings.findByAccountId(id)) {
            try {
                recordsService.remove(actor, "calendar_event", m.getRecordId(), null);
            } catch (Exception ignored) {
                // già cancellato a mano: va bene
            }
        }
        mappings.deleteAll(mappings.findByAccountId(id));
        google.revoke(cipher.decrypt(a.getRefreshTokenEnc()));
        accounts.delete(a);
    }

    @Scheduled(fixedDelayString = "${app.calendar.sync-delay-ms:900000}")
    public void syncAll() {
        for (CalendarAccount a : accounts.findByActiveTrue()) {
            try {
                sync(a);
                a.setLastError(null);
            } catch (Exception e) {
                // un account rotto (token revocato su Google) non ferma gli altri, e l'errore resta visibile
                log.warn("Calendary sync failed for {}", a.getAccountEmail(), e);
                a.setLastError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(500, e.getMessage().length())));
            }
            a.setLastSyncAt(Instant.now());
            accounts.save(a);
        }
    }

    // niente @Transactional: ogni evento passa da RecordsService, che ha la sua. Un evento rotto non deve riportare indietro
    // i 200 importati prima di lui
    void sync(CalendarAccount a) {
        String token = accessToken(a);
        Actor actor = actorOf(a.getUserId());
        String pageToken = null;
        while (true) {
            GoogleCalendarClient.Page page;
            try {
                page = google.events(token, a.getCalendarId(), a.getSyncToken(), pageToken);
            } catch (GoogleCalendarClient.SyncTokenExpired e) {
                // 410: si riparte da capo. Trattarlo come errore fermerebbe il sync per sempre, in silenzio
                a.setSyncToken(null);
                pageToken = null;
                continue;
            }
            for (Map<String, Object> ev : page.items()) apply(a, actor, ev);
            if (page.nextPageToken() != null) {
                pageToken = page.nextPageToken();
                continue;
            }
            a.setSyncToken(page.nextSyncToken());
            return;
        }
    }

    @SuppressWarnings("unchecked")
    private void apply(CalendarAccount a, Actor actor, Map<String, Object> ev) {
        String id = String.valueOf(ev.get("id"));
        Optional<EventMapping> existing = mappings.findByAccountIdAndExternalEventId(a.getId(), id);
        if ("cancelled".equals(ev.get("status"))) {
            existing.ifPresent(m -> {
                try {
                    recordsService.remove(actor, "calendar_event", m.getRecordId(), null);
                } catch (Exception ignored) { }
                mappings.delete(m);
            });
            return;
        }
        String etag = String.valueOf(ev.get("etag"));
        if (existing.isPresent() && etag.equals(existing.get().getEtag())) return; // non cambiato

        Map<String, Object> start = (Map<String, Object>) ev.getOrDefault("start", Map.of());
        Map<String, Object> end = (Map<String, Object>) ev.getOrDefault("end", Map.of());
        boolean allDay = start.get("date") != null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", ev.get("summary") == null ? "(senza titolo)" : ev.get("summary"));
        data.put("start", instant(start));
        if (!end.isEmpty()) data.put("end", instant(end));
        data.put("allDay", allDay);
        if (ev.get("location") != null) data.put("location", ev.get("location"));
        if (ev.get("description") != null) data.put("description", ev.get("description"));
        data.put("externalId", id);
        data.put("calendarAccount", a.getId().toString());
        UpsertRecordDto dto = new UpsertRecordDto();
        dto.setData(data);
        dto.setOwnerId(a.getUserId());

        // via RecordsService: workflow e audit scattano come per un evento creato a mano
        if (existing.isPresent()) {
            recordsService.update(actor, "calendar_event", existing.get().getRecordId(), dto, null);
            existing.get().setEtag(etag);
        } else {
            Record r = recordsService.create(actor, "calendar_event", dto, null);
            EventMapping m = new EventMapping();
            m.setAccountId(a.getId());
            m.setExternalEventId(id);
            m.setRecordId(r.getId());
            m.setEtag(etag);
            mappings.save(m);
        }
    }

    // tutto il giorno: la data diventa la mezzanotte nel fuso del workspace. Convertirla come un istante UTC la sposterebbe
    // di un giorno per metà del mondo
    private String instant(Map<String, Object> t) {
        if (t.get("dateTime") != null) return OffsetDateTime.parse(t.get("dateTime").toString()).toInstant().toString();
        return LocalDate.parse(t.get("date").toString()).atStartOfDay(ZoneId.of(zone)).toInstant().toString();
    }

    private String accessToken(CalendarAccount a) {
        if (a.getAccessTokenEnc() != null && a.getAccessTokenExpiresAt() != null && a.getAccessTokenExpiresAt().isAfter(Instant.now())) {
            return cipher.decrypt(a.getAccessTokenEnc());
        }
        GoogleCalendarClient.Tokens t = google.refresh(cipher.decrypt(a.getRefreshTokenEnc()));
        a.setAccessTokenEnc(cipher.encrypt(t.accessToken()));
        a.setAccessTokenExpiresAt(t.expiresAt());
        return t.accessToken();
    }

    private Actor actorOf(UUID userId) {
        User u = userRepository.findById(userId).orElseThrow();
        return new Actor(u.getId(), ActorType.USER, u.getEmail(), userRoleRepository.findByUser_Id(userId)
                .stream().map(ur -> ur.getRole().getId()).toList());
    }
}
