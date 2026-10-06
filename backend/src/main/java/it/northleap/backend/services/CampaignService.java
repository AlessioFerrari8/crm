package it.northleap.backend.services;


import it.northleap.backend.dtos.*;
import it.northleap.backend.entities.Campaign;
import it.northleap.backend.entities.CampaignRecipient;
import it.northleap.backend.entities.CampaignStatus;
import it.northleap.backend.entities.RecipientStatus;
import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.CampaignRecipientRepository;
import it.northleap.backend.repositories.CampaignRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final RecordsService recordsService;
    private final CampaignRenderer campaignRenderer;
    private final EmailService emailService;
    private final AuditService auditService;

    public List<Campaign> list() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }
    public Campaign get(UUID id) {
        return campaignRepository.findById(id).orElseThrow(() -> new NotFoundException("Campagna non trovata"));
    }

    @Transactional
    public Campaign create(Actor actor, CampaignDto dto) {
        Campaign c = new Campaign();
        c.setTrackingSecret(randomToken());
        c.setCreatedBy(actor.id());
        apply(c, dto);
        return campaignRepository.save(c);
    }

    @Transactional
    public Campaign update(UUID id, CampaignDto dto) {
        Campaign c = get(id);
        // partita è partita: cambiare il testo a metà invio vuol dire due campagne diverse con lo stesso nome
        if (c.getStatus() != CampaignStatus.DRAFT) throw new BadRequestException("Si modifica solo una campagna in bozza");
        apply(c, dto);
        return c;
    }

    @Transactional
    public void delete(UUID id) {
        Campaign c = get(id);
        if (c.getStatus() != CampaignStatus.DRAFT) throw new BadRequestException("Una campagna già inviata resta nello storico");
        campaignRepository.delete(c);
    }

    // chi riceverebbe, adesso. Stesso filtro e stessi permessi della vista del modulo
    public CampaignAudience audience(Actor actor, UUID id) {
        Campaign c = get(id);
        long[] n = new long[5];
        Set<String> seen = new HashSet<>();
        List<String> sample = new ArrayList<>();
        forEachTarget(actor, c, r -> {
            n[0]++;
            String email = email(r);
            if (email == null) { n[2]++; return; }
            if (optedOut(r)) { n[1]++; return; }
            if (!seen.add(email)) { n[3]++; return; }
            if (CampaignRenderer.missesVariables(c, r)) n[4]++;
            if (sample.size() < 10) sample.add(email);
        });
        return new CampaignAudience(n[0], seen.size(), n[1], n[2], n[3], n[4], sample);
    }

    // a te, prima che tutti: col primo record del filtro come dati
    public void test(Actor actor, UUID id) {
        Campaign c = get(id);
        Record[] first = new Record[1];
        forEachTarget(actor, c, r -> { if (first[0] == null && email(r) != null) first[0] = r; });
        if (first[0] == null) throw new BadRequestException("Il filtro non trova nessun destinatario con email");
        CampaignRenderer.Rendered out = campaignRenderer.render(c, first[0], "test");
        emailService.sendMessage(new MailTransport.Outgoing(actor.email(), "[PROVA] " + c.getSubject(), out.html(), out.text(), out.headers()));
    }

    // materializza i destinatari e mette SENDING. da qui in poi è storia: la lista non si ricalcola
    @Transactional
    public Campaign send(Actor actor, UUID id, long confirmCount, String ip) {
        Campaign c = get(id);
        if (c.getStatus() == CampaignStatus.PAUSED) {
            c.setStatus(CampaignStatus.SENDING);
            return c;
        }

        if (c.getStatus() != CampaignStatus.DRAFT) throw new BadRequestException("Campagna gia' inviata");
        if (!emailService.isConfigured()) throw new BadRequestException("Invio email non configurato (MAIL_PROVIDER)");

        Set<String> seen = new HashSet<>();
        List<CampaignRecipient> rows = new ArrayList<>();
        forEachTarget(actor, c, r -> {
            String email = email(r);
            // l'opt-out si ricontrolla anche all'invio del singolo messaggio: qui serve solo a non accodarlo
            if (email == null || optedOut(r) || !seen.add(email)) return;
            CampaignRecipient cr = new CampaignRecipient();
            cr.setCampaign(c);
            cr.setRecordId(r.getId());
            cr.setEmail(email);
            cr.setToken(randomToken());
            rows.add(cr);
        });
        // il numero digitato dall'utente deve essere quello di adesso: se nel frattempo il filtro e'
        // cambiato, si riconferma
        if (rows.size() != confirmCount) {
            throw new BadRequestException("I destinatari ora sono " + rows.size() + ", non " + confirmCount + ": ricontrolla e riconferma");
        }
        campaignRecipientRepository.saveAll(rows);
        c.setStatus(CampaignStatus.SENDING);
        c.setStartedAt(Instant.now());
        auditService.log(actor, "campaign.send", "campaign", id.toString(), Map.of("recipients", rows.size()), ip);
        return c;
    }

    @Transactional
    public Campaign pause(UUID id) {
        Campaign c = get(id);
        if (c.getStatus() != CampaignStatus.SENDING) throw new BadRequestException("Si mette in pausa solo una campagna in invio");
        c.setStatus(CampaignStatus.PAUSED);
        return c;
    }

    public CampaignStats stats(UUID id) {
        return new CampaignStats(
                campaignRecipientRepository.countByCampaign_IdAndStatus(id, RecipientStatus.PENDING),
                campaignRecipientRepository.countByCampaign_IdAndStatus(id, RecipientStatus.SENT),
                campaignRecipientRepository.countByCampaign_IdAndStatus(id, RecipientStatus.FAILED),
                campaignRecipientRepository.countByCampaign_IdAndStatus(id, RecipientStatus.SKIPPED),
                campaignRecipientRepository.countByCampaign_IdAndOpenedAtIsNotNull(id),
                campaignRecipientRepository.countByCampaign_IdAndClickedAtIsNotNull(id));
    }

    public List<CampaignRecipient> recipients(UUID id, RecipientStatus status) {
        return status == null
                ? campaignRecipientRepository.findTop500ByCampaign_IdOrderByEmail(id)
                : campaignRecipientRepository.findTop500ByCampaign_IdAndStatusOrderByEmail(id, status);
    }

    // pagina per pagina con RecordsService.query: RBAC e scope della vista, niente scorciatoie
    private void forEachTarget(Actor actor, Campaign c, Consumer<Record> each) {
        int page = 1;
        while (true) {
            QueryRecordsDto q = new QueryRecordsDto();
            q.setFilter(c.getTargetFilter());
            q.setPage(page);
            q.setPageSize(200);
            RecordQueryResponse res = recordsService.query(actor, c.getTargetObjectKey(), q);
            res.items().forEach(each);
            if ((long) page * res.pageSize() >= res.total() || res.items().isEmpty()) return;
            page++;
        }
    }

    static String email(Record r) {
        Object v = r.getData() == null ? null : r.getData().get("email");
        if (v == null) return null;
        String s = v.toString().trim().toLowerCase();
        return s.contains("@") ? s : null;
    }

    static boolean optedOut(Record r) {
        return r.getData() != null && Boolean.TRUE.equals(r.getData().get("marketingOptOut"));
    }

    private void apply(Campaign c, CampaignDto dto) {
        c.setName(dto.name());
        c.setSubject(dto.subject());
        c.setPreheader(dto.preheader());
        c.setBodyHtml(dto.bodyHtml());
        c.setBodyText(dto.bodyText());
        c.setTargetObjectKey(dto.targetObjectKey());
        c.setTargetFilter(dto.targetFilter());
    }

    static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
