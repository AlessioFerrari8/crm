package it.northleap.backend.dtos;

// Vista "profilo" del workspace, riservata agli admin (GET /api/workspace/profile): brand + anagrafica
// fiscale. Questi campi non vanno sul GET pubblico /api/workspace, che resta WorkspaceBrandResponse e
// lo legge ogni autenticato per la topbar
public record WorkspaceProfileResponse(
        String name,
        String brandColor,
        String logoUrl,
        String calcomUrl,
        String legalName,
        String vatNumber,
        String taxCode,
        String address,
        String contactLine
) {
}
