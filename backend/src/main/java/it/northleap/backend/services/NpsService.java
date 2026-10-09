package it.northleap.backend.services;

import it.northleap.backend.dtos.NpsStats;
import it.northleap.backend.entities.NpsResponse;
import it.northleap.backend.entities.NpsSurvey;
import it.northleap.backend.entities.Record;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.NpsResponseRepository;
import it.northleap.backend.repositories.NpsSurveyRepository;
import it.northleap.backend.repositories.RecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NpsService {

    // sotto questa soglia il numero è rumore, e la UI lo deve chiaramente dire
    private static final int MIN_ANSWERS = 30;

    private final NpsSurveyRepository surveyRepository;
    private final NpsResponseRepository responseRepository;
    private final RecordRepository recordRepository;
    private final EmailService emailService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public record PublicQuestion(String question) { }

    public List<NpsSurvey> list() {
        return surveyRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public NpsSurvey save(UUID id, NpsSurvey in) {
        NpsSurvey s = id == null ? new NpsSurvey() : get(id);
        s.setName(in.getName());
        s.setQuestion(in.getQuestion());
        s.setActive(in.isActive());
        s.setCooldownDays(Math.max(0, in.getCooldownDays()));
        return surveyRepository.save(s);
    }

    // il cooldown sta qui, non nel workflow: chi configura l'automazione non deve ricordarsi di non tempestare
    @Transactional
    public Optional<NpsResponse> invite(UUID surveyId, UUID contactId) {
        NpsSurvey s = get(surveyId);
        if (!s.isActive()) return Optional.empty();
        Record contact = recordRepository.findByIdAndIsDeletedFalse(contactId).orElse(null);
        String email = contact == null ? null : CampaignService.email(contact);
        if (email == null || CampaignService.optedOut(contact)) return Optional.empty();
        if (responseRepository.existsByRecordIdAndSentAtAfter(contactId, Instant.now().minus(s.getCooldownDays(), ChronoUnit.DAYS))) {
            return Optional.empty();
        }
        NpsResponse r = new NpsResponse();
        r.setSurvey(s);
        r.setRecordId(contactId);
        r.setToken(CampaignService.randomToken());
        r.setSentAt(Instant.now());
        responseRepository.save(r);

        String link = frontendBaseUrl + "/nps/" + r.getToken();
        emailService.send(email, s.getName(), "<p>" + HtmlUtils.htmlEscape(s.getQuestion()) + "</p>"
                + "<p><a href=\"" + link + "\">Rispondi con un clic (da 0 a 10)</a></p>");
        return Optional.of(r);
    }

    // 410 se già risposto: il link resta nella casella, e un secondo voto falserebbe il numero
    @Transactional(readOnly = true)
    public PublicQuestion question(String token) {
        NpsResponse r = byToken(token);
        return new PublicQuestion(r.getSurvey().getQuestion());
    }

    @Transactional
    public void answer(String token, int score, String comment, String ip) {
        if (score < 0 || score > 10) throw new BadRequestException("Il voto da 0 a 10");
        NpsResponse r = byToken(token);
        r.setScore(score);
        r.setComment(comment == null ? null : comment.strip().substring(0, Math.min(comment.strip().length(), 2000)));
        r.setAnsweredAt(Instant.now());
        r.setRemoteIp(ip);
    }

    public NpsStats stats(UUID surveyId) {
        long sent = responseRepository.countBySurvey_id(surveyId);
        List<NpsResponse> answers = responseRepository.findBySurvey_IdAndScoreIsNotNull(surveyId);
        long promoters = answers.stream().filter(a -> a.getScore() >= 9).count();
        long detractors = answers.stream().filter(a -> a.getScore() <= 6).count();
        long n = answers.size();
        Integer nps = n == 0 ? null : (int) Math.round(100.0 * (promoters - detractors) / n);
        int rate = sent == 0 ? 0 : (int) Math.round(100.0 * n / sent);
        return new NpsStats(nps, sent, n, promoters, n - promoters - detractors, detractors, rate, n < MIN_ANSWERS);
    }

    private NpsSurvey get(UUID id) {
        return surveyRepository.findById(id).orElseThrow(() -> new NotFoundException("Sondaggio non trovato"));
    }

    private NpsResponse byToken(String token) {
        NpsResponse r = responseRepository.findByToken(token).orElseThrow(() -> new NotFoundException("Link non valido"));
        if (r.getScore() != null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE, "Hai già risposto, grazie");
        }
        return r;
    }

}
