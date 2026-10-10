package it.northleap.backend.services;

import it.northleap.backend.dtos.ApiKeyCreatedResponse;
import it.northleap.backend.dtos.CreateApiKeyDto;
import it.northleap.backend.entities.ApiKey;
import it.northleap.backend.entities.Role;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.ApiKeyRepository;
import it.northleap.backend.repositories.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private RoleRepository roleRepository;

    private ApiKeyService newService() {
        return new ApiKeyService(apiKeyRepository, roleRepository);
    }

    @Test
    void createGeneratesPrefixedKeyAndStoresOnlyHash() {
        ApiKeyService svc = newService();
        when(roleRepository.findByKey("agent")).thenReturn(Optional.of(new Role()));
        CreateApiKeyDto dto = new CreateApiKeyDto();
        dto.setName("integration");
        dto.setRoleKey("agent");

        ApiKeyCreatedResponse result = svc.create(dto);

        assertNotNull(result.apiKey());
        assertTrue(result.apiKey().startsWith("nl_"));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertEquals(HashUtil.sha256Hex(result.apiKey()), saved.getKeyHash());
        assertEquals(result.apiKey().substring(0, 10), saved.getPrefix());
    }

    // una chiave senza ruolo risponderebbe 403 a tutto: si rifiuta alla creazione
    @Test
    void createWithoutRoleIsRefused() {
        CreateApiKeyDto dto = new CreateApiKeyDto();
        dto.setName("integration");
        assertThrows(it.northleap.backend.exceptions.BadRequestException.class, () -> newService().create(dto));
    }

    @Test
    void createResolvesRoleWhenRoleKeyProvided() {
        ApiKeyService svc = newService();
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setKey("admin");
        when(roleRepository.findByKey("admin")).thenReturn(Optional.of(role));

        CreateApiKeyDto dto = new CreateApiKeyDto();
        dto.setName("integration");
        dto.setRoleKey("admin");
        svc.create(dto);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertEquals(role, captor.getValue().getRole());
    }

    @Test
    void revokeSetsRevokedAt() {
        ApiKeyService svc = newService();
        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        svc.revoke(key.getId());

        assertNotNull(key.getRevokedAt());
        verify(apiKeyRepository).save(key);
    }

    @Test
    void revokeThrowsWhenNotFound() {
        ApiKeyService svc = newService();
        UUID id = UUID.randomUUID();
        when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> svc.revoke(id));
    }
}
