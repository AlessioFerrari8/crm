package it.northleap.backend.services;

import it.northleap.backend.dtos.ApiKeyCreatedResponse;
import it.northleap.backend.dtos.ApiKeySummary;
import it.northleap.backend.dtos.CreateApiKeyDto;
import it.northleap.backend.entities.ApiKey;
import it.northleap.backend.entities.Role;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ApiKeyRepository;
import it.northleap.backend.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// Porting di ApiKeysService (api-keys.module.ts).
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final RoleRepository roleRepository;

    public List<ApiKeySummary> list() {
        return apiKeyRepository.findByRevokedAtIsNullOrderByCreatedAtDesc().stream()
                .map(k -> new ApiKeySummary(k.getId(), k.getName(), k.getPrefix(), k.getLastUsedAt(), k.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ApiKeyCreatedResponse create(CreateApiKeyDto dto) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String raw = "nl_" + HexFormat.of().formatHex(bytes);

        Role role = dto.getRoleKey() != null
                ? roleRepository.findByKey(dto.getRoleKey()).orElse(null)
                : null;

        // una chiave senza ruolo ha roleIds vuoto: RbacService nega tutto e ogni chiamata risponde 403.
        // sembra rotta, quindi non si crea
        if (role == null) {
            throw new it.northleap.backend.exceptions.BadRequestException("Scegli un ruolo per la chiave");
        }

        ApiKey key = new ApiKey();
        key.setName(dto.getName());
        key.setKeyHash(HashUtil.sha256Hex(raw));
        key.setPrefix(raw.substring(0, 10));
        key.setRole(role);
        apiKeyRepository.save(key);

        return new ApiKeyCreatedResponse(raw);
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiKey non trovata"));
        key.setRevokedAt(Instant.now());
        apiKeyRepository.save(key);
    }
}
