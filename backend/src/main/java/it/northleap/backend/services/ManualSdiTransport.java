package it.northleap.backend.services;

import it.northleap.backend.entities.EInvoice;
import org.springframework.stereotype.Component;

@Component
public class ManualSdiTransport implements SdiTransport {
    @Override public String name() { return "manual"; }
    @Override public boolean isConfigured() { return true; }
    @Override public SdiSendResult send(EInvoice invoice) { return new SdiSendResult(false, null); }
}
