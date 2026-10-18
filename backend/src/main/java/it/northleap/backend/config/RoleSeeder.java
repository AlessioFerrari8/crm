package it.northleap.backend.config;

import it.northleap.backend.entities.Role;
import it.northleap.backend.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// Seed dei ruoli di sistema: solo key/label, i permessi li semina PermissionSeeder (deve girare dopo, vedi @Order)
@Component
@RequiredArgsConstructor
@Order(1)
public class RoleSeeder implements ApplicationRunner {

    // ruoli chiave, valore
    private static final Map<String, String> SYSTEM_ROLES = Map.of(
            "owner", "Owner",
            "admin", "Admin",
            "manager", "Manager",
            "agent", "Agent",
            // chi si registra da solo (AuthService.register): lavora sui record, niente admin
            "member", "Member",
            "viewer", "Viewer"
    );

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        // solo i ruoli che mancano: un DB già popolato prima di "member" lo riceve al riavvio
        List<Role> roles = SYSTEM_ROLES.entrySet().stream()
                .filter(entry -> roleRepository.findByKey(entry.getKey()).isEmpty())
                .map(entry -> {
                    Role role = new Role();
                    role.setKey(entry.getKey());
                    role.setLabel(entry.getValue());
                    role.setSystem(true);
                    return role;
                })
                .toList();

        if (!roles.isEmpty()) {
            roleRepository.saveAll(roles);
        }
    }
}
