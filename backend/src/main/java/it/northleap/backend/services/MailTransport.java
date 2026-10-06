package it.northleap.backend.services;

public interface MailTransport {
    // identificativo del provider
    String name();

    // false = credenziali mancanti
    boolean isConfigured();

    boolean send(String from, String to, String subject, String html);

    // messaggio completo per le campagne: parte testo, header List-Unsubscribe. Lancia invece di restituire false,
    // perché il destinatario FAILED deve dire perché
    record Outgoing(String to, String subject, String html, String text, java.util.Map<String, String> headers) { }

    // default per i trasporti che non sanno fare di meglio: niente testo, niente header
    default void sendMessage(Outgoing m) {
        if (!send(null, m.to(), m.subject(), m.html())) {
            throw new IllegalStateException("Trasport " + name() + " refused the message");
        }
    }
}
