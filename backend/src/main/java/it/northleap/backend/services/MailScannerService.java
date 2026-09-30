package it.northleap.backend.services;

import it.northleap.backend.entities.MailboxConfig;
import it.northleap.backend.repositories.MailboxConfigRepository;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailScannerService {
    private final MailboxConfigRepository mailboxConfigRepository;
    private final MailMessageImporter mailMessageImporter;
    private final SecretCipher secretCipher;

    // il lock dura 5 minuti: più del giro più lungo plausibile, ma sempre meno dell'intervallo tra
    // due giri consecutivi bloccati
    private static final Duration LOCK = Duration.ofMinutes(5);

    // fixedDelay conta dalla fine del giro precendente: infatti se una scansione dura tre minuti, la
    // successiva non parte comunque. Con un cron però partirebbe
    // @EnableScheduling è già attivo (lo usa WorkflowScheduler)
    @Scheduled(fixedDelayString = "${app.mailscanner.delay-ms:120000}")
    public void scanAll() {
        for (MailboxConfig listed : mailboxConfigRepository.findByActiveTrue()) {
            Optional<MailboxConfig> locked = acquire(listed.getId());
            if (locked.isEmpty()) continue; // qualcun altro sta girando
            MailboxConfig box = locked.get();

            try {
                scan(box);
                box.setLastError(null);
            } catch (Exception e) {
                // un errore su una casella non deve fermare le altre
                log.warn("Mailbox scan failed for {}", box.getLabel(), e);
                box.setLastError(e.getMessage());
            } finally {
                box.setLockedUntil(null);
                box.setLastScanAt(Instant.now());
                mailboxConfigRepository.save(box);
            }
        }
    }

    // il lock sta sulla riga, non sull'oggetto in memoria: la lista di scanAll è caricata a inizio giro
    // e quando si arriva a una casella può essere vecchia di minuti (nel frattempo "Scarica ora" può
    // averla già scansionata). Dopo il lock la casella si rilegge, così lastUid è quello vero e il save
    // finale non lo riporta indietro: altrimenti la stessa mail diventa due ticket
    Optional<MailboxConfig> acquire(UUID id) {
        Instant now = Instant.now();
        if (mailboxConfigRepository.tryLock(id, now, now.plus(LOCK)) == 0) {
            return Optional.empty();
        }
        return mailboxConfigRepository.findById(id);
    }


    void scan(MailboxConfig box) throws MessagingException {
        String protocol = box.isSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        // senza questi due un server che accetta la connessione e poi tace tiene il thread dello scheduler per sempre,
        // e non da nessun errore. Il prefisso deve essere quello del protocollo scelto, non "imaps" fisso:
        // con ssl=false le due proprietà sarebbero ignorate
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "30000");

        Session session = Session.getInstance(props);
        try (Store store = session.getStore()) {
            store.connect(box.getHost(), box.getPort(), box.getUsername(),
                    secretCipher.decrypt(box.getPasswordEnc()));

            Folder folder = store.getFolder(box.getFolder());
            folder.open(Folder.READ_ONLY);        // vedi la nota sotto
            UIDFolder uf = (UIDFolder) folder;

            // getMessagesByUID è inclusivo su entrambi gli estremi: si parte
            // da last+1, o si rilegge l'ultimo messaggio a ogni giro
            Message[] msgs = uf.getMessagesByUID(box.getLastUid() + 1,
                    UIDFolder.LASTUID);
            long max = box.getLastUid();
            for (Message m : msgs) {
                long uid = uf.getUID(m);
                if (uid <= box.getLastUid()) continue;
                try {
                    mailMessageImporter.importMessage(box, m);
                } catch (Exception e) {
                    // un messaggio malformato non deve bloccare la coda per
                    // sempre: si logga, si va avanti, l'UID avanza comunque
                    log.warn("Cannot import message uid={}", uid, e);
                }
                max = Math.max(max, uid);
            }
            box.setLastUid(max);
            folder.close(false);
        }
    }
}
