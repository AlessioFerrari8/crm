package it.northleap.backend.controllers;

import it.northleap.backend.dtos.CreateRoleDto;
import it.northleap.backend.dtos.RoleResponse;
import it.northleap.backend.dtos.SetPermissionsDto;
import it.northleap.backend.security.PermAction;
import it.northleap.backend.security.RequirePerm;
import it.northleap.backend.services.RolesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Ruoli e permessi gestiti sotto la risorsa RBAC "user" (chi gestisce gli utenti gestisce anche
// ruoli/permessi), stessa scelta del riferimento (rbac.module.ts).
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolesController {

    private final RolesService rolesService;

    @GetMapping
    @RequirePerm(resource = "user", action = PermAction.READ)
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(rolesService.list());
    }

    @PostMapping
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleDto dto) {
        return ResponseEntity.ok(rolesService.create(dto));
    }

    @PutMapping("/{id}/permissions")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<RoleResponse> setPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody SetPermissionsDto dto
    ) {
        return ResponseEntity.ok(rolesService.setPermissions(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePerm(resource = "user", action = PermAction.WRITE)
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        rolesService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
