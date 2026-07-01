package it.northleap.backend.dtos;

import it.northleap.backend.entities.PermScope;

import java.util.List;
import java.util.UUID;

// ruolo + la sua matrice permessi, composta a livello di DTO (niente collection bidirezionale su
// Role, stesso pattern di WorkflowDetailResponse). `system` = ruolo di sistema non eliminabile.
public record RoleResponse(
        UUID id,
        String key,
        String label,
        boolean system,
        List<PermissionView> permissions
) {
    public record PermissionView(
            String resource,
            boolean canRead,
            boolean canWrite,
            boolean canExecute,
            PermScope scope
    ) {
    }
}
