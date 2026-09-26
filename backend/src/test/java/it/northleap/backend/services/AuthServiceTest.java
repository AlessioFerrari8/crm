package it.northleap.backend.services;

import it.northleap.backend.dtos.MeResponse;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.User;
import it.northleap.backend.entities.UserRole;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.RoleRepository;
import it.northleap.backend.repositories.SessionRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.repositories.WorkspaceRepository;
import it.northleap.backend.security.JwtService;
import it.northleap.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MfaService mfaService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, jwtService, userRepository, workspaceRepository,
                sessionRepository, roleRepository, userRoleRepository, passwordEncoder, mfaService);
    }

    // principal e' null quando la richiesta arriva autenticata via X-Api-Key invece che JWT
    // (Actor != UserPrincipal) - prima di questo fix andava in NullPointerException non gestita
    @Test
    void meThrowsCleanBadRequestWhenPrincipalIsNull() {
        assertThrows(BadRequestException.class, () -> service.me(null));
    }

    @Test
    void meReturnsUserWithResolvedRoles() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@test.com");
        user.setName("Test User");
        UserPrincipal principal = new UserPrincipal(user);

        Role role = new Role();
        role.setKey("owner");
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        when(userRoleRepository.findByUser_Id(user.getId())).thenReturn(List.of(userRole));

        MeResponse response = service.me(principal);

        assertEquals("u@test.com", response.email());
        assertEquals(List.of("owner"), response.roles());
    }
}
