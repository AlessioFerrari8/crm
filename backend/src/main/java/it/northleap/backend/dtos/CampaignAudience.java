package it.northleap.backend.dtos;

import java.util.List;

// il conteggio che il pulsante di invio deve dire: "1.240 destinatari, 37 disiscritti saltati"
public record CampaignAudience(
        long matched,
        long sendable,
        long optedOut,
        long noEmail,
        long duplicates,
        long missingVariables,
        List<String> sample) {

}
