package it.northleap.backend.services;

import it.northleap.backend.AbstractIntegrationTest;
import it.northleap.backend.entities.MailboxConfig;
import it.northleap.backend.repositories.MailboxConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// il lock deve reggere sul database vero: con un mock l'UPDATE condizionata non la prova nessuno.
// Niente @Transactional sulla classe: acquire committa da solo, ed è proprio quello che si prova
class MailboxLockTest extends AbstractIntegrationTest {

    @Autowired private MailScannerService mailScannerService;
    @Autowired private MailboxConfigRepository mailboxConfigRepository;

    private MailboxConfig box;

    @BeforeEach
    void seed() {
        MailboxConfig m = new MailboxConfig();
        m.setLabel("Assistenza");
        m.setHost("imap.example.test");
        m.setUsername("supporto@example.test");
        m.setPasswordEnc("non-usata");
        m.setActive(false); // lo scheduler non deve provare a connettersi durante il test
        box = mailboxConfigRepository.save(m);
    }

    @AfterEach
    void cleanup() {
        mailboxConfigRepository.deleteById(box.getId());
    }

    @Test
    void la_seconda_scansione_non_prende_il_lock() {
        assertThat(mailScannerService.acquire(box.getId())).isPresent();
        assertThat(mailScannerService.acquire(box.getId())).isEmpty();
    }

    @Test
    void un_lock_scaduto_si_riprende() {
        box.setLockedUntil(Instant.now().minusSeconds(60));
        mailboxConfigRepository.save(box);

        assertThat(mailScannerService.acquire(box.getId())).isPresent();
    }

    // il caso della review: lo scheduler arriva con una copia caricata prima che "Scarica ora" finisse.
    // acquire deve restituire la riga di adesso, non quella vecchia
    @Test
    void dopo_il_lock_la_casella_e_quella_del_database() {
        MailboxConfig stale = mailboxConfigRepository.findById(box.getId()).orElseThrow();

        MailboxConfig fresh = mailboxConfigRepository.findById(box.getId()).orElseThrow();
        fresh.setLastUid(42);
        mailboxConfigRepository.save(fresh);

        MailboxConfig locked = mailScannerService.acquire(stale.getId()).orElseThrow();
        assertThat(locked.getLastUid()).isEqualTo(42);
        assertThat(locked.getLockedUntil()).isAfter(Instant.now());
    }
}
