package it.northleap.backend.services;

import it.northleap.backend.dtos.UpdateWorkspaceDto;
import it.northleap.backend.dtos.WorkspaceBrandResponse;
import it.northleap.backend.dtos.WorkspaceProfileResponse;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// App single-tenant: esiste un solo Workspace (creato all'onboarding, che si auto-blocca dopo il
// primo), quindi "il workspace" e' sempre il primo per data di creazione - stesso accesso usato
// da AuthService.isOnboarded().
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    // vista brand pubblica (topbar): solo name/brandColor/logoUrl
    public WorkspaceBrandResponse get() {
        return toResponse(currentWorkspace());
    }

    // vista profilo (solo admin): per ora i campi brand, la Fase 1 vi aggiungera' l'anagrafica org
    public WorkspaceProfileResponse getProfile() {
        Workspace ws = currentWorkspace();
        return new WorkspaceProfileResponse(ws.getName(), ws.getBrandColor(), ws.getLogoUrl(), ws.getCalcomUrl(),
                ws.getLegalName(), ws.getVatNumber(), ws.getTaxCode(), ws.getAddress(), ws.getContactLine());
    }

    @Transactional
    public WorkspaceBrandResponse update(UpdateWorkspaceDto dto) {
        Workspace workspace = currentWorkspace();
        workspace.setName(dto.getName());

        // brandColor/logoUrl: aggiornati solo se forniti, altrimenti restano com'erano
        if (dto.getBrandColor() != null) {
            workspace.setBrandColor(dto.getBrandColor());
        }
        if (dto.getLogoUrl() != null) {
            workspace.setLogoUrl(dto.getLogoUrl());
        }
        // calcomUrl: null = non toccare, vuoto = rimuovere; se presente deve essere https
        if (dto.getCalcomUrl() != null) {
            String url = dto.getCalcomUrl().trim();
            if (!url.isEmpty() && !url.startsWith("https://")) {
                throw new BadRequestException("Il link Cal.com deve iniziare con https://");
            }
            workspace.setCalcomUrl(url.isEmpty() ? null : url);
        }
        // anagrafica fiscale, stessa regola: null = non toccare, "" = svuotare
        workspace.setLegalName(blankToNull(dto.getLegalName(), workspace.getLegalName()));
        workspace.setVatNumber(blankToNull(dto.getVatNumber(), workspace.getVatNumber()));
        workspace.setTaxCode(blankToNull(dto.getTaxCode(), workspace.getTaxCode()));
        workspace.setAddress(blankToNull(dto.getAddress(), workspace.getAddress()));
        workspace.setContactLine(blankToNull(dto.getContactLine(), workspace.getContactLine()));

        workspaceRepository.save(workspace);
        return toResponse(workspace);
    }

    // helper
    // null = lascia com'era, stringa vuota = svuota, altrimenti trim
    private String blankToNull(String incoming, String current) {
        if (incoming == null) return current;
        String v = incoming.trim();
        return v.isEmpty() ? null : v;
    }

    private Workspace currentWorkspace() {
        return workspaceRepository.findTopByOrderByCreatedAtAsc()
                .orElseThrow(() -> new NotFoundException("Workspace non configurato"));
    }

    private WorkspaceBrandResponse toResponse(Workspace workspace) {
        return new WorkspaceBrandResponse(
                workspace.getName(), workspace.getBrandColor(), workspace.getLogoUrl(), workspace.getCalcomUrl());
    }
}
