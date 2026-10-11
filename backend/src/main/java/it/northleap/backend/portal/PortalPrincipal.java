package it.northleap.backend.portal;


import java.util.UUID;

// chi sta usando il portale. Non è un Actor: se lo fosse, tutto il resto del backend lo tratterebbe come un
// utente interno
public record PortalPrincipal(UUID portalUserId, UUID contactId, String email) {
    public static final String REQUEST_ATTRIBUTE = "portalPrincipal";
}
