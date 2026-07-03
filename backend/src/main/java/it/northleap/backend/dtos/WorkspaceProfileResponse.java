package it.northleap.backend.dtos;

// Vista "profilo" del workspace, riservata agli admin (GET /api/workspace/profile). Per ora
// rispecchia i campi brand; la Fase 1 la espandera' con l'anagrafica org (ragione sociale,
// P.IVA, sede, contatti...) - quei campi sensibili NON vanno sul GET pubblico /api/workspace.
public record WorkspaceProfileResponse(String name, String brandColor, String logoUrl, String calcomUrl) {
}
