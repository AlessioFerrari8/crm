package it.northleap.backend.services;

import it.northleap.backend.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Igiene della tabella session: le righe scadute o revocate restano nel DB per sempre (login,
// refresh-rotation, logout, cambio/reset password ne producono in continuazione) senza servire
// più a nessuno — il refresh le rifiuta comunque. Pulizia notturna di quelle più vecchie di
// GRACE_DAYS (il margine conserva un minimo di storia recente utile in un'indagine).
@Component
@RequiredArgsConstructor
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private static final int GRACE_DAYS = 7;

    private final SessionRepository sessionRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now().minus(GRACE_DAYS, ChronoUnit.DAYS);
        long expired = sessionRepository.deleteByExpiresAtBefore(cutoff);
        long revoked = sessionRepository.deleteByRevokedAtBefore(cutoff);
        if (expired + revoked > 0) {
            log.info("Pulizia sessioni: rimosse {} scadute e {} revocate (cutoff {})", expired, revoked, cutoff);
        }
    }
}
