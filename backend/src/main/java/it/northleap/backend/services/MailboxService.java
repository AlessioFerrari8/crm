package it.northleap.backend.services;

import it.northleap.backend.dtos.MailboxDto;
import it.northleap.backend.dtos.UpsertMailboxDto;
import it.northleap.backend.entities.MailboxConfig;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.MailboxConfigRepository;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailboxService {

    private final MailboxConfigRepository mailboxConfigRepository;
    private final SecretCipher secretCipher;
    private final MailScannerService mailScannerService;

    public List<MailboxDto> list() {
        return mailboxConfigRepository.findAll().stream().map(MailboxDto::of).toList();
    }

    @Transactional
    public MailboxDto create(UpsertMailboxDto dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BadRequestException("Password is required");
        }
        MailboxConfig m = new MailboxConfig();
        apply(m, dto);
        return MailboxDto.of(mailboxConfigRepository.save(m));
    }

    @Transactional
    public MailboxDto update(UUID id, UpsertMailboxDto dto) {
        MailboxConfig m = find(id);
        apply(m, dto);
        return MailboxDto.of(mailboxConfigRepository.save(m));
    }

    @Transactional
    public void remove(UUID id) {
        mailboxConfigRepository.delete(find(id));
    }

    // prova la connessione e basta: non importa niente, non muove lastUid.
    // Chi configura una casella IMAP sbaglia porta o TLS al primo tentativo, sempre
    // Questo è metà dell'usabilità della pagina
    public String test(UUID id) {
        MailboxConfig m = find(id);
        String protocol = m.isSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "10000");

        try (Store store = Session.getInstance(props).getStore()) {
            store.connect(m.getHost(), m.getPort(), m.getUsername(), secretCipher.decrypt(m.getPasswordEnc()));
            Folder f = store.getFolder(m.getFolder());
            f.open(Folder.READ_ONLY);
            int count = f.getMessageCount();
            f.close(false);
            return "OK: " + count + " messaggi in " + m.getFolder();
        } catch (Exception e) {
            // messaggio vero, non "errore di connessione"
            throw new BadRequestException(e.getMessage());
        }
    }

    // scansione a mano, per il collaudo. Prende lo stesso lock dello scheduler: senza, i due giri
    // leggono lo stesso lastUid e la stessa mail diventa due ticket (o due note). Niente
    // @Transactional: il lock va committato subito, se no lo scheduler non lo vede, e un messaggio
    // che fallisce segnerebbe rollback-only tutta la scansione
    public void scanNow(UUID id) {
        MailboxConfig m = find(id);
        if (!mailScannerService.acquire(m)) {
            throw new BadRequestException("Scansione già in corso su questa casella, riprova fra poco");
        }
        try {
            mailScannerService.scan(m);
            m.setLastError(null);
        } catch (Exception e) {
            m.setLastError(e.getMessage());
            throw new BadRequestException(e.getMessage());
        } finally {
            m.setLockedUntil(null);
            m.setLastScanAt(Instant.now());
            mailboxConfigRepository.save(m);
        }
    }

    private MailboxConfig find(UUID id) {
        return mailboxConfigRepository.findById(id).orElseThrow(() -> new NotFoundException("Mailbox not found"));
    }

    private void apply(MailboxConfig m, UpsertMailboxDto dto) {
        m.setLabel(dto.getLabel());
        m.setHost(dto.getHost());
        m.setPort(dto.getPort());
        m.setSsl(dto.isSsl());
        m.setUsername(dto.getUsername());
        m.setFolder(dto.getFolder());
        m.setTargetObjectKey(dto.getTargetObjectKey());
        m.setDefaultOwnerId(dto.getDefaultOwnerId());
        m.setActive(dto.isActive());
        // password vuota in update = non la cambiare: la UI non la rimanda
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            m.setPasswordEnc(secretCipher.encrypt(dto.getPassword()));
        }

    }
}
