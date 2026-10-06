package it.northleap.backend.services;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    // host + email
    @Value("${spring.mail.host:}")
    private String host;

    @Value("${app.mail.from:}")
    private String fromEmail;

    public SmtpMailTransport(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public boolean isConfigured() {
        return host != null && !host.isBlank()
                && fromEmail != null && !fromEmail.isBlank()
                && mailSenderProvider.getIfAvailable() != null;
    }

    // multipart/alternative con la parte testo, e gli header che Gmail e Outlook leggono per decidere se sei spam.
    // Le eccezioni risalgono: la registra il destinatario della campagna
    @Override
    public void sendMessage(Outgoing m) {
        if (!isConfigured()) throw new IllegalStateException("SMTP non configurato");
        try {
            JavaMailSender mailSender = mailSenderProvider.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(m.to());
            helper.setSubject(m.subject());
            helper.setText(m.text(), m.html());
            for (var h : m.headers().entrySet()) message.setHeader(h.getKey(), h.getValue());
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public boolean send(String from, String to, String subject, String html) {
        if (!isConfigured()) {
            log.debug("SMTP non configurato: invio a {} saltato", to);
            return false;
        }

        // il from per-azione dell'azione send_email dei workflow NON e' supportato su SMTP: si usa
        // sempre app.mail.from. Un mittente diverso dalla mailbox autenticata viene rifiutato dalla
        // maggior parte dei server (553/550 sender rejected), e siccome send() cattura tutto e
        // ritorna false il workflow fallirebbe in silenzio - meglio un comportamento prevedibile.
        if (from != null && !from.isBlank() && !from.equalsIgnoreCase(fromEmail)) {
            log.debug("SMTP: from '{}' ignorato, si usa il mittente configurato '{}'", from, fromEmail);
        }
        String sender = fromEmail;
        try {
            JavaMailSender mailSender = mailSenderProvider.getObject();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("Invio SMTP a {} fallito: {}", to, e.getMessage());
            return false;
        }
    }
}
