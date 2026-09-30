package it.northleap.backend.services;

import it.northleap.backend.dtos.UpsertRecordDto;
import it.northleap.backend.entities.MailboxConfig;
import it.northleap.backend.entities.Record;
import it.northleap.backend.entities.User;
import it.northleap.backend.repositories.RecordRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailMessageImporter {

    private final RecordsService recordsService;
    private final RecordRepository recordRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final FileStorageService fileStorageService;
    private final NotesService notesService;

    // il corpo oltre questa soglia si taglia: una mail con dentro un preventivo incollato
    // dieci volte esiste davvero
    private static final int MAX_BODY = 100_000;

    // il token che le risposte in uscita devono riportare nell'oggetto
    private static final Pattern THREAD = Pattern.compile("\\[#NL-([0-9a-f]{8})]");

    @Transactional
    public void importMessage(MailboxConfig box, Message message) throws Exception {
        if (isAutoReply(message)) {
            // un "fuori sede" che crea un ticket che manda una notifica che genera un "fuori sede": la prima
            // volta sono 400 ticket
            log.info("Skipping auto-reply on mailbox {}", box.getLabel());
            return;
        }

        String subject = message.getSubject() == null ? "(senza oggetto)" : message.getSubject();

        String from = firstAddress(message);
        String body = textOf(message);

        // 1: il token nell'oggetto è l'aggancio più affidabile
        Matcher m = THREAD.matcher(subject);
        if (m.find()) {
            Optional<Record> existing = byThreadKey(m.group(1));
            if (existing.isPresent()) {
                appendToTicket(box, existing.get(), from, body);
                return;
            }
        }

        Actor actor = actorOf(box);
        UpsertRecordDto dto = new UpsertRecordDto();
        Map<String, Object> data = new HashMap<>();
        data.put("subject", subject.replaceAll("\\[#NL-[0-9a-f]{8}]", "").trim());
        data.put("description", body);
        data.put("fromEmail", from);
        data.put("receivedAt", message.getSentDate() == null ? Instant.now().toString() : message.getSentDate().toInstant().toString());
        data.put("status", "open");
        // la chiave con cui le risposte torneranno a casa. Va scritta dal primo giorno: se non è negli
        // oggetti di oggi, le risposte di domani non si agganciano a niente
        data.put("threadKey", UUID.randomUUID().toString().substring(0, 8));
        // Il messageId serve al secondo criterio di aggancio (In reply to)
        data.put("messageId", header(message, "Message-Id"));
        dto.setData(data);
        dto.setOwnerId(box.getDefaultOwnerId());

        Record created = recordsService.create(actor, box.getTargetObjectKey(), dto, "mailscanner");

        storeAttachments(message, created.getId(), box.getDefaultOwnerId());
    }

    // NON creare contatti automaticamente: la prima newsletter di spam che passa ti fonda l'anagrafica
    // Qui si cerca e basta, se non c'è, resta fromEmail dentro data
    private Actor actorOf(MailboxConfig box) {
        UUID ownerId = box.getDefaultOwnerId();
        if (ownerId == null) {
            throw new IllegalStateException("Mailbox " + box.getLabel() + " has no default owner");
        }
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Default owner of mailbox " + box.getLabel() + " not found"));
        List<UUID> roleIds = userRoleRepository.findByUser_Id(ownerId).stream()
                .map(ur -> ur.getRole().getId())
                .toList();

        return new Actor(user.getId(), ActorType.USER, user.getEmail(), roleIds);
    }

    // cinque righe di guardia, e la prima volta che servono risparmiano una giornata
    private boolean isAutoReply(Message message) throws Exception {
        String auto = header(message, "Auto-Submitted");
        if (auto != null && !auto.equalsIgnoreCase("no")) return true;
        if (header(message, "X-Autoreply") != null) return true;
        if (header(message, "X-Autorespond") != null) return true;
        String precedence = header(message, "Precedence");
        if (precedence != null && List.of("bulk", "list", "junk")
                .contains(precedence.toLowerCase())) return true;
        String from = firstAddress(message);
        if (from == null) return true;
        String f = from.toLowerCase();

        return f.startsWith("mailer-daemon@") || f.startsWith("postmaster@")
                || f.contains("noreply@") || f.contains("no-reply@");
    }

    private String header(Message message, String name) throws Exception {
        String[] v = message.getHeader(name);
        return (v == null || v.length == 0) ? null : v[0];
    }

    private String firstAddress(Message message) throws Exception {
        var from = message.getFrom();
        if (from == null || from.length == 0) return null;
        return from[0] instanceof InternetAddress ia ? ia.getAddress() : from[0].toString();
    }

    // text/plain se c'è, altrimenti l'HTML ridotto a testo. jsoup arriva già con la fase 5 (build.gradle:65), nessuna
    // dipendeza nuova
    private String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return truncate(String.valueOf(part.getContent()));
        }
        if (part.isMimeType("text/html")) {
            return truncate(Jsoup.parse(String.valueOf(part.getContent())).text());
        }
        if (part.getContent() instanceof Multipart mp) {
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part p = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) continue;
                if (p.isMimeType("text/plain")) {
                    return truncate(String.valueOf(p.getContent()));
                }
                if (p.isMimeType("text/html") && html == null) {
                    html = String.valueOf(p.getContent());
                }
                if (p.getContent() instanceof Multipart) {
                    String nested = textOf(p);
                    if (!nested.isBlank()) return nested;
                }
            }
            if (html != null) return truncate(Jsoup.parse(html).text());
        }
        return "";
    }

    private String truncate(String s) {
        return s.length() <= MAX_BODY ? s : s.substring(0, MAX_BODY) + "\n[...]";
    }

    // gli allegati passano da FileStorageService, quindi anche dai controlli su BLOCKED_EXTENSIONS, che è
    // esattamente quello che voglio, così nessuno deve poter mettere un .exe nel CRM mandando una mail
    private void storeAttachments(Message message, UUID recordId, UUID actorId) throws Exception {
        if (!(message.getContent() instanceof Multipart mp)) return;
        for (int i = 0; i < mp.getCount(); i++) {
            Part p = mp.getBodyPart(i);
            if (!Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) continue;
            if (p.getFileName() == null) continue;
            try (var in = p.getInputStream()) {
                fileStorageService.store(
                        new InMemoryMultipartFile(MimeUtility.decodeText(p.getFileName()),
                                p.getContentType(), in.readAllBytes()), actorId, recordId);
            } catch (Exception e) {
                // un allegato rifiutato non deve far perdere il ticket
                log.warn("Attachment skipped on record {}: {}", recordId, e.getMessage());
            }
        }
    }

    private Optional<Record> byThreadKey(String key) {
        // findAllReferencing fa `data::text like %key%` su tutti i record: con una chiave da 8 esadecimali
        // va bene, ed evita una query nuova
        return recordRepository.findAllReferencing(key).stream().findFirst();
    }

    // una risposta non è un secondo ticket: è una nota sul primo, e se era chiuso lo riapre
    private void appendToTicket(MailboxConfig box, Record ticket, String from, String body) {
        Actor actor = actorOf(box);
        var note = new it.northleap.backend.dtos.CreateNoteDto();

        // createNoteDto.body è @NotBlank @Size(max = 4000), ma @Valid scatta solo nel controller: qui nessuno
        // lo controlla, e NotesService.create fa getBody().trim()
        String text = "Da " + (from == null ? "mittente sconosciuto" : from) + ":\n" + (body.isBlank() ? "(vuoto)" : body);
        note.setBody(text.length() > 4000 ? text.substring(0, 3990) + "\n[...]" : text );
        notesService.create(actor, ticket.getId(), note);
        if ("closed".equals(ticket.getStatus())) {
            ticket.setStatus("open");
            recordRepository.save(ticket);
        }
    }



}
