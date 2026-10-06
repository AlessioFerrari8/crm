package it.northleap.backend.services;

import it.northleap.backend.dtos.TimeEntryDto;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.TimeEntry;
import it.northleap.backend.entities.User;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.exceptions.RbacDeniedException;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.TimeEntryRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.entities.Record;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeService {

    private static final String RESOURCE = "time";

    private final TimeEntryRepository timeEntryRepository;
    private final RecordRepository recordRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;

    // oltre questi giorni una registrazione la tocca solo chi ha scope ALL: il consuntivo di un progetto
    // chiuso non deve cambiare da solo
    @Value("${app.time.lock-after-days:7}")
    private int lockAfterDays;

    @Value("${app.time.auto-close-hours:12}")
    private int autoCloseHours;

    // se c'è n'è già uno attivo lo chiude e ne apre uno nuovo: mai un errore, è quello che l'utente si aspetta
    @Transactional
    public TimeEntry start(Actor actor, UUID recordId) {
        requireUser(actor);
        scope(actor, PermAction.WRITE);
        Record record = recordRepository.findByIdAndIsDeletedFalse(recordId)
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
        timeEntryRepository.findByUserIdAndEndedAtIsNull(actor.id()).ifPresent(t -> {
            close(t, Instant.now());
            // flush: l'indice parziale uq_time_entry_running vede ancora il vecchio aperto finché l'update non è
            // partito, e l'insert qui sotto violerebbe questo vincolo
            timeEntryRepository.saveAndFlush(t);
        });
        TimeEntry t = new TimeEntry();
        t.setUserId(actor.id());
        t.setRecordId(recordId);
        t.setObjectKey(record.getObjectType().getKey());
        t.setStartedAt(Instant.now());
        return timeEntryRepository.save(t);
    }

    @Transactional
    public TimeEntry stop(Actor actor) {
        requireUser(actor);
        TimeEntry t = timeEntryRepository.findByUserIdAndEndedAtIsNull(actor.id())
                .orElseThrow(() -> new BadRequestException("Nessun timer attivo"));
        close(t, Instant.now());
        return t;
    }

    public TimeEntry running(Actor actor) {
        requireUser(actor);
        return timeEntryRepository.findByUserIdAndEndedAtIsNull(actor.id()).orElse(null);
    }

    @Transactional
    public TimeEntry create(Actor actor, TimeEntryDto dto) {
        requireUser(actor);
        scope(actor, PermAction.WRITE);
        if (dto.minutes() == null) throw new BadRequestException("Indica i minuti");
        Record record = recordRepository.findByIdAndIsDeletedFalse(dto.recordId())
                .orElseThrow(() -> new NotFoundException("Record non trovato"));
        TimeEntry t = new TimeEntry();
        t.setUserId(actor.id());
        t.setRecordId(dto.recordId());
        t.setObjectKey(record.getObjectType().getKey());
        apply(t, dto);
        t.setHourlyRate(rateOf(actor.id()));
        return timeEntryRepository.save(t);
    }

    @Transactional
    public TimeEntry update(Actor actor, UUID id, TimeEntryDto dto) {
        TimeEntry t = editable(actor, id);
        if (t.getEndedAt() == null) throw new BadRequestException("Ferma il timer prima di modificarlo");
        apply(t, dto);
        return t;
    }

    @Transactional
    public void delete(Actor actor, UUID id) {
        timeEntryRepository.delete(editable(actor, id));
    }

    public List<TimeEntry> list(Actor actor, UUID recordId, UUID userId, Instant from, Instant to) {
        PermScope scope = scope(actor, PermAction.READ);
        // scope OWN: un agente vede le sue registrazioni, qualunque userId chieda
        UUID effectiveUser = scope == PermScope.OWN ? actor.id() : userId;
        return timeEntryRepository.search(recordId, effectiveUser, from != null ? from : Instant.EPOCH,
                to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS));
    }

    public List<TimeEntryRepository.SummaryRow> summary(Actor actor, UUID recordId) {
        scope(actor, PermAction.READ);
        return timeEntryRepository.summary(recordId);
    }

    // il timer lasciato aperto per il weekend; senza il lunedì qualcuno ha 63 ore su un ticket
    @Scheduled(cron = "${app.time.auto-close-cron: 0 0 3 * * *}")
    @Transactional
    public void autoClose() {
        Instant limit = Instant.now().minus(autoCloseHours, ChronoUnit.HOURS);
        for (TimeEntry t : timeEntryRepository.findByEndedAtIsNullAndStartedAtBefore(limit)) {
            // si chiude a startedAt + N ore, non adesso: "adesso" varrebbe 63 ore
            close(t, t.getStartedAt().plus(autoCloseHours, ChronoUnit.HOURS));
            t.setAutoClosed(true);
            log.info("Timer {} of user {} closed automatically", t.getId(), t.getUserId());
        }
    }

    private void close(TimeEntry t, Instant end) {
        t.setEndedAt(end);
        // arrotondato per eccesso al minuto: un timer da 20 s vale 1 min, non 0
        t.setMinutes((int) Math.max(1, Math.ceil(Duration.between(t.getStartedAt(), end).toSeconds() / 60.0)));
        t.setHourlyRate(rateOf(t.getUserId()));
    }

    private void apply(TimeEntry t, TimeEntryDto dto) {
        t.setStartedAt(dto.startedAt());
        if (dto.minutes() != null) {
            t.setMinutes(dto.minutes());
            t.setEndedAt(dto.startedAt().plus(dto.minutes(), ChronoUnit.MINUTES));
        }
        if (dto.billable() != null) t.setBillable(dto.billable());
        t.setDescription(dto.description());
    }

    private TimeEntry editable(Actor actor, UUID id) {
        PermScope scope = scope(actor, PermAction.WRITE);
        TimeEntry t = timeEntryRepository.findById(id).orElseThrow(() -> new NotFoundException("Registrazione non trovata"));
        if (scope == PermScope.OWN && !t.getUserId().equals(actor.id())) {
            // 404 e non 403: con scope OWN le registrazioni degli altri non esistono
            throw new NotFoundException("Registrazione non trovata");
        }
        boolean old = t.getStartedAt().isBefore(Instant.now().minus(lockAfterDays, ChronoUnit.DAYS));
        if (old && scope != PermScope.ALL) {
            throw new RbacDeniedException("Registrazione piu' vecchia di " + lockAfterDays + " giorni: serve un responsabile");
        }
        return t;
    }

    private java.math.BigDecimal rateOf(UUID userId) {
        return userRepository.findById(userId).map(User::getHourlyRate).orElse(null);
    }

    private PermScope scope(Actor actor, PermAction action) {
        RbacService.Resolution r = rbacService.resolve(actor.roleIds(), RESOURCE, action);
        if (!r.allowed()) throw new RbacDeniedException("Permesso negato su time");
        return r.scope();
    }

    // il timer è di una persona: una API key non ha un cronometro
    private void requireUser(Actor actor) {
        if (actor.type() != ActorType.USER) throw new BadRequestException("Il timer richiede un utente, non una API key");
    }
}
