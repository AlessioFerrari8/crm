package it.northleap.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

// Invio email via Resend (stessa integrazione dell'azione send_email dei workflow, qui come
// service riusabile: email manuali dal record, notifiche via email). Se RESEND_API_KEY /
// RESEND_FROM_EMAIL non sono configurate, isConfigured()=false e chi chiama decide come degradare.
// Destinazione HTTP fissa (api.resend.com): nessuna superficie SSRF.
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final List<MailTransport> transports;

    @Value("${app.mail.provider:}")
    private String provider;

    public EmailService(List<MailTransport> transports) {
        this.transports = transports;
    }

    private MailTransport transport() {
        if (provider == null || provider.isBlank()) return null;

        return transports.stream()
                .filter(t -> t.name().equalsIgnoreCase(provider))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("app.mail.provider='{}' sconosciuto: email disattivate", provider);
                    return null;
                });
    }

    public boolean isConfigured() {
        MailTransport t = transport();
        return t != null && t.isConfigured();
    }

    public boolean send(String to, String subject, String html) {
        return send(null, to, subject, html);
    }

    public boolean send(String from, String to, String subject, String html) {
        MailTransport t = transport();
        if (t == null && !t.isConfigured()) {
            log.debug("Nessun trasporto email configurato: invio a {} saltato", to);
            return false;
        }
        return t.send(from, to, subject, html);
    }



}
