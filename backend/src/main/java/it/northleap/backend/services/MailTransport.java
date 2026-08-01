package it.northleap.backend.services;

public interface MailTransport {
    // identificativo del provider
    String name();

    // false = credenziali mancanti
    boolean isConfigured();

    boolean send(String from, String to, String subject, String html);
}
