package it.northleap.backend.entities;

import java.util.Set;

public enum EInvoiceStatus {
    DRAFT,      // generata e validata, si può ancora rigenerare
    READY,      // pronta da caricare sul portale del provider
    SENT,       // caricata: da qui la fattura non si tocca più
    DELIVERED,  // RC o MC dallo SDI
    REJECTED,   // NS: la fattura si corregge e si ritrasmette con un progressivo nuovo
    ACCEPTED;   // NE EC01, solo PA

    // gli stati in cui la fattura (record e righe) non è più modificabile, né cancellabile
    public static final Set<EInvoiceStatus> LOCKING = Set.of(READY, SENT, DELIVERED, ACCEPTED);
}
