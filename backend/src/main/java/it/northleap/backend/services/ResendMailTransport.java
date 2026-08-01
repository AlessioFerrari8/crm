package it.northleap.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class ResendMailTransport implements MailTransport {
    private static final Logger log = LoggerFactory.getLogger(ResendMailTransport.class);

    private final RestClient restClient = RestClient.create();

    @Value("${app.resend.api-key:}")
    private String apiKey;

    @Value("${app.resend.from-email:}")
    private String fromEmail;

    @Override
    public String name() {
        return "resend";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && fromEmail != null && !fromEmail.isBlank();
    }

    public boolean send(String to, String subject, String html) {
        return send(fromEmail, to, subject, html);
    }

    // from esplicito perché l'azione send_email dei workflow lo sovrascrive per singola azione
    // true se Resend ha accettato la richiesta (2xx)
    public boolean send(String from, String to, String subject, String html) {
        if (!isConfigured()) {
            log.debug("EmailService non configurato: invio a {} saltato", to);
            return false;
        }
        // senza fallback un from null farebbe esplodere Map.of, che non accetta valori null
        if (from == null || from.isBlank()) {
            from = fromEmail;
        }
        try {
            int status = restClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", from, "to", to, "subject", subject, "html", html))
                    .exchange((req, resp) -> resp.getStatusCode().value());
            if (status < 200 || status >= 300) {
                log.warn("Resend ha risposto {} per l'invio a {}", status, to);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Invio email a {} fallito: {}", to, e.getMessage());
            return false;
        }

    }

}
