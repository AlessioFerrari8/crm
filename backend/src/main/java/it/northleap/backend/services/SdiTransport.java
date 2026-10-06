package it.northleap.backend.services;

import it.northleap.backend.entities.EInvoice;

// stessa forma di MailTransport: quando arriverà un provider vero (Aruba, Fatture in Cloud...) si scrive semplicemente
// la seconda implementazione e basta. Generatore, entità ed anche la UI non si toccano
public interface SdiTransport {
    String name();

    boolean isConfigured();

    // sent=false: il file è pronto, ma non è partito (trasporto manuale)
    SdiSendResult send(EInvoice invoice);

    record SdiSendResult(boolean sent, String sdiIdentifier) { }
}
