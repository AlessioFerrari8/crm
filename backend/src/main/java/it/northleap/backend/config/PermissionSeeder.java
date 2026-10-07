package it.northleap.backend.config;

import it.northleap.backend.entities.ObjectType;
import it.northleap.backend.entities.PermScope;
import it.northleap.backend.entities.Permission;
import it.northleap.backend.entities.Role;
import it.northleap.backend.repositories.ObjectTypeRepository;
import it.northleap.backend.repositories.PermissionRepository;
import it.northleap.backend.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/*
* automazione della sicurezza
* ogni volta che viene aggiunto un nuovo object type il sistema
* deve sapere i vari ruoli
*/


@Component
@RequiredArgsConstructor
@Order(3)
public class PermissionSeeder implements ApplicationRunner {

    private static final List<String> FIXED_RESOURCES = List.of("page", "chart", "workflow", "user", "apikey", "time", "campaign");

    // inject
    private final RoleRepository roleRepository;
    private final ObjectTypeRepository objectTypeRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<String> objectTypeKeys = objectTypeRepository.findAll().stream().map(ObjectType::getKey).toList();
        List<String> resources = Stream.concat(FIXED_RESOURCES.stream(), objectTypeKeys.stream()).distinct().toList();

        List<Permission> toCreate = new ArrayList<>();
        for (Role role : roleRepository.findAll()) {
            for (String resource : resources) {
                if (permissionRepository.findByRole_IdAndResource(role.getId(), resource).isEmpty()) {
                    toCreate.add(buildPermission(role, resource));
                }
            }
        }
        if (!toCreate.isEmpty()) {
            permissionRepository.saveAll(toCreate);
        }
    }

    // vari permessi, in base al ruolo assegnato
    private Permission buildPermission(Role role, String resource) {
        Permission permission = new Permission();
        permission.setRole(role);
        permission.setResource(resource);
        permission.setScope(PermScope.ALL);

        switch (role.getKey()) {
            case "owner", "admin", "manager" -> {
                permission.setCanRead(true);
                permission.setCanWrite(true);
                permission.setCanExecute(true);
            }
            case "agent" -> {
                permission.setCanRead(true);
                permission.setCanWrite(true);
                // le ore: un agente vede e scrive solo le sue, il manager invece quelle di tutti
                if ("time".equals(resource)) permission.setScope(PermScope.OWN);
                // una campagna manda migliaia di email che non si annullano: all'agente solo lettura
                if ("campaign".equals(resource)) permission.setCanWrite(false);
            }
            case "viewer" -> permission.setCanRead(true);
            default -> {
                // ruoli custom futuri: nessun permesso di default
            }
        }

        return permission;
    }
}
