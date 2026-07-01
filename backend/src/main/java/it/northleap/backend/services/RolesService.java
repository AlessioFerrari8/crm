package it.northleap.backend.services;

import it.northleap.backend.dtos.CreateRoleDto;
import it.northleap.backend.dtos.PermissionItemDto;
import it.northleap.backend.dtos.RoleResponse;
import it.northleap.backend.dtos.SetPermissionsDto;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.Permission;
import it.northleap.backend.entities.Role;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.PermissionRepository;
import it.northleap.backend.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Gestione runtime di ruoli e matrice permessi (porting di rbac.module.ts). Le entità Role/
// Permission esistevano già (seed + RbacService), mancava solo la superficie REST per gestirle.
@Service
@RequiredArgsConstructor
public class RolesService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public List<RoleResponse> list() {
        List<Role> roles = roleRepository.findAll();
        roles.sort(Comparator.comparing(Role::getKey));
        // una sola query sui permessi, raggruppati per ruolo (niente N+1)
        Map<UUID, List<Permission>> byRole = permissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getRole().getId()));
        return roles.stream()
                .map(r -> toResponse(r, byRole.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleDto dto) {
        if (roleRepository.findByKey(dto.getKey()).isPresent()) {
            throw new BadRequestException("Ruolo già esistente");
        }
        Role role = new Role();
        role.setKey(dto.getKey());
        role.setLabel(dto.getLabel());
        role.setSystem(false);
        role = roleRepository.save(role);
        return toResponse(role, List.of());
    }

    // upsert per (role, resource): aggiorna la riga se esiste, altrimenti la crea
    @Transactional
    public RoleResponse setPermissions(UUID roleId, SetPermissionsDto dto) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BadRequestException("Ruolo inesistente"));
        for (PermissionItemDto item : dto.getPermissions()) {
            Permission perm = permissionRepository.findByRole_IdAndResource(roleId, item.getResource())
                    .orElseGet(() -> {
                        Permission p = new Permission();
                        p.setRole(role);
                        p.setResource(item.getResource());
                        return p;
                    });
            perm.setCanRead(item.isCanRead());
            perm.setCanWrite(item.isCanWrite());
            perm.setCanExecute(item.isCanExecute());
            perm.setScope(item.getScope() != null ? item.getScope() : PermScope.ALL);
            permissionRepository.save(perm);
        }
        return toResponse(role, permissionRepository.findByRole_Id(roleId));
    }

    @Transactional
    public void remove(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BadRequestException("Ruolo inesistente"));
        if (role.isSystem()) {
            throw new BadRequestException("Ruolo di sistema non eliminabile");
        }
        permissionRepository.deleteByRole_Id(roleId);
        roleRepository.delete(role);
    }

    private RoleResponse toResponse(Role role, List<Permission> perms) {
        List<RoleResponse.PermissionView> views = perms.stream()
                .map(p -> new RoleResponse.PermissionView(
                        p.getResource(), p.isCanRead(), p.isCanWrite(), p.isCanExecute(), p.getScope()))
                .toList();
        return new RoleResponse(role.getId(), role.getKey(), role.getLabel(), role.isSystem(), views);
    }
}
