package it.northleap.backend.services;

import it.northleap.backend.dtos.LoginRequest;
import it.northleap.backend.dtos.MeResponse;
import it.northleap.backend.dtos.RegisterRequest;
import it.northleap.backend.dtos.VerifyEmailRequest;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.User;
import it.northleap.backend.entities.UserRole;
import it.northleap.backend.exceptions.EmailNotVerifiedException;
import it.northleap.backend.exceptions.RegistrationClosedException;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private EmailService emailService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, jwtService, userRepository, workspaceRepository,
                sessionRepository, roleRepository, userRoleRepository, passwordEncoder, mfaService, emailService);
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

    @Test
    void registerIsRefusedWhenRegistrationIsDisabled() {
        // default: registrationEnabled = false
        assertThrows(RegistrationClosedException.class, () -> service.register(new RegisterRequest("Mario",
                "m@test.com", "Abcdef1!"), null));
    }

    @Test
    void regsiterIsRefusedBeforeOnboarding() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        when(workspaceRepository.findTopByOrderByCreatedAtAsc()).thenReturn(java.util.Optional.empty());

        assertThrows(BadRequestException.class, () -> service.register(new RegisterRequest("Mario", "m@test.com", "Abcdef1!"), null));
    }

    @Test
    void registerRejectsTakenEmailIgnoringCase() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        var ws = new it.northleap.backend.entities.Workspace();
        ws.setOnboarded(true);
        when(workspaceRepository.findTopByOrderByCreatedAtAsc()).thenReturn(java.util.Optional.of(ws));
        when(userRepository.existsByEmailIgnoreCase("mario@test.com")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.register(new RegisterRequest("Mario",
                " Mario@Test.com ", "Abcdef1!"), null));
        assertEquals("EMAIL_TAKEN", ex.getCode());
    }

    @Test
    void registerRejectsWeakPassword() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        var ws = new it.northleap.backend.entities.Workspace();
        ws.setOnboarded(true);
        when(workspaceRepository.findTopByOrderByCreatedAtAsc()).thenReturn(java.util.Optional.of(ws));

        assertThrows(BadRequestException.class, () -> service.register(new RegisterRequest("Mario", "m@test.com", "password"), null));
    }

    @Test
    void loginIsRefusedWhenEmailIsNotVerified() {
        User u = new User();
        u.setEmailVerified(false);
        org.springframework.security.core.Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new UserPrincipal(u));
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        assertThrows(EmailNotVerifiedException.class, () -> service.login(new LoginRequest("m@test.com", "Abcdef1!"), null));
    }

    @Test
    void registerIsRefusedWhenVerificationIsRequiredButMailIsNotConfigured() {
        ReflectionTestUtils.setField(service, "registrationEnabled", true);
        ReflectionTestUtils.setField(service, "requireEmailVerification", true);
        var ws = new it.northleap.backend.entities.Workspace();
        ws.setOnboarded(true);
        when(workspaceRepository.findTopByOrderByCreatedAtAsc()).thenReturn(Optional.of(ws));
        when(emailService.isConfigured()).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.register(new RegisterRequest("Mario", "m@test.com", "Abcdef1!x"), null));
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(userRepository.findByVerificationTokenHash(HashUtil.sha256Hex("abc"))).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.verifyEmail(new VerifyEmailRequest("abc")));
        assertEquals("VERIFICATION_INVALID", ex.getCode());
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        User u = new User();
        u.setEmailVerified(false);
        u.setVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationTokenHash(HashUtil.sha256Hex("abc"))).thenReturn(Optional.of(u));

        assertThrows(BadRequestException.class, () -> service.verifyEmail(new VerifyEmailRequest("abc")));
        assertEquals(false, u.isEmailVerified());
    }

    @Test
    void verifyEmailConfirmsUserAndConsumesToken() {
        User u = new User();
        u.setEmailVerified(false);
        u.setVerificationTokenHash(HashUtil.sha256Hex("abc"));
        u.setVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByVerificationTokenHash(HashUtil.sha256Hex("abc"))).thenReturn(Optional.of(u));

        service.verifyEmail(new VerifyEmailRequest("abc"));

        assertTrue(u.isEmailVerified());
        assertNull(u.getVerificationTokenHash());
        assertNull(u.getVerificationExpiresAt());
    }

}
