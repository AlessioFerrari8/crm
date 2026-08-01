package it.northleap.backend.services;

import it.northleap.backend.dtos.AcceptInviteDto;
import it.northleap.backend.dtos.InviteUserDto;
import it.northleap.backend.dtos.UpdateUserDto;
import it.northleap.backend.entities.Invite;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.Session;
import it.northleap.backend.entities.User;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.repositories.InviteRepository;
import it.northleap.backend.repositories.RoleRepository;
import it.northleap.backend.repositories.SessionRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private InviteRepository inviteRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private EmailService emailService;

    private UserService service;
    private Actor actor;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, inviteRepository, userRoleRepository,
                sessionRepository, passwordEncoder, auditService, emailService);
        // frontendBaseUrl e' iniettato con @Value: in un test Mockito puro resterebbe null e il
        // link finirebbe "null/invite?token=..."
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4200");
        actor = new Actor(UUID.randomUUID(), ActorType.USER, "admin@test.com", List.of());
    }

    private Role role(String key) {
        Role r = new Role();
        r.setId(UUID.randomUUID());
        r.setKey(key);
        return r;
    }

    @Test
    void inviteRejectsUnknownRole() {
        when(roleRepository.findByKey("ghost")).thenReturn(Optional.empty());
        InviteUserDto dto = new InviteUserDto();
        dto.setEmail("new@test.com");
        dto.setRoleKey("ghost");

        assertThrows(BadRequestException.class, () -> service.invite(actor, dto));
    }

    @Test
    void inviteRejectsExistingEmail() {
        when(roleRepository.findByKey("agent")).thenReturn(Optional.of(role("agent")));
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        InviteUserDto dto = new InviteUserDto();
        dto.setEmail("dup@test.com");
        dto.setRoleKey("agent");

        assertThrows(BadRequestException.class, () -> service.invite(actor, dto));
    }

    @Test
    void inviteSavesHashedTokenNotRawToken() {
        when(roleRepository.findByKey("agent")).thenReturn(Optional.of(role("agent")));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        lenient().when(userRepository.getReferenceById(actor.id())).thenReturn(new User());

        InviteUserDto dto = new InviteUserDto();
        dto.setEmail("new@test.com");
        dto.setRoleKey("agent");
        var result = service.invite(actor, dto);

        var captor = org.mockito.ArgumentCaptor.forClass(Invite.class);
        verify(inviteRepository).save(captor.capture());
        assertEquals(HashUtil.sha256Hex(result.inviteToken()), captor.getValue().getTokenHash());
        verify(auditService).log(actor, "invite", "user", "new@test.com", null, null);
    }

    @Test
    void inviteSendsEmailWithTheActivationLinkWhenMailIsConfigured() {
        when(roleRepository.findByKey("agent")).thenReturn(Optional.of(role("agent")));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        lenient().when(userRepository.getReferenceById(actor.id())).thenReturn(new User());
        when(emailService.isConfigured()).thenReturn(true);

        InviteUserDto dto = new InviteUserDto();
        dto.setEmail("new@test.com");
        dto.setRoleKey("agent");
        var result = service.invite(actor, dto);

        var html = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("new@test.com"), eq("Invito a Northleap CRM"), html.capture());
        // il link deve contenere il token in chiaro: e' l'unica volta che viene esposto
        assertTrue(html.getValue().contains("http://localhost:4200/invite?token=" + result.inviteToken()));
    }

    // l'invito non deve mai fallire per un problema di posta: il token torna comunque
    // nella response e l'admin puo' passare il link a mano
    @Test
    void inviteStillSucceedsWhenMailIsNotConfigured() {
        when(roleRepository.findByKey("agent")).thenReturn(Optional.of(role("agent")));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        lenient().when(userRepository.getReferenceById(actor.id())).thenReturn(new User());
        when(emailService.isConfigured()).thenReturn(false);

        InviteUserDto dto = new InviteUserDto();
        dto.setEmail("new@test.com");
        dto.setRoleKey("agent");
        var result = service.invite(actor, dto);

        assertNotNull(result.inviteToken());
        verify(inviteRepository).save(any(Invite.class));
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    void acceptRejectsInvalidToken() {
        when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        AcceptInviteDto dto = new AcceptInviteDto();
        dto.setToken("nope");
        dto.setName("Mario");
        dto.setPassword("Password123!");

        assertThrows(BadRequestException.class, () -> service.accept(dto));
    }

    @Test
    void acceptRejectsExpiredInvite() {
        Invite invite = new Invite();
        invite.setEmail("x@test.com");
        invite.setRole(role("agent"));
        invite.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.of(invite));

        AcceptInviteDto dto = new AcceptInviteDto();
        dto.setToken("tok");
        dto.setName("Mario");
        dto.setPassword("Password123!");

        assertThrows(BadRequestException.class, () -> service.accept(dto));
    }

    @Test
    void acceptRejectsAlreadyAcceptedInvite() {
        Invite invite = new Invite();
        invite.setEmail("x@test.com");
        invite.setRole(role("agent"));
        invite.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        invite.setAcceptedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.of(invite));

        AcceptInviteDto dto = new AcceptInviteDto();
        dto.setToken("tok");
        dto.setName("Mario");
        dto.setPassword("Password123!");

        assertThrows(BadRequestException.class, () -> service.accept(dto));
    }

    @Test
    void acceptCreatesUserWithInviteRole() {
        Role agentRole = role("agent");
        Invite invite = new Invite();
        invite.setEmail("x@test.com");
        invite.setRole(agentRole);
        invite.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.of(invite));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");

        AcceptInviteDto dto = new AcceptInviteDto();
        dto.setToken("tok");
        dto.setName("Mario");
        dto.setPassword("Password123!");
        var result = service.accept(dto);

        assertEquals("x@test.com", result.email());
        verify(userRepository).save(any(User.class));
        var urCaptor = org.mockito.ArgumentCaptor.forClass(it.northleap.backend.entities.UserRole.class);
        verify(userRoleRepository).save(urCaptor.capture());
        assertEquals(agentRole, urCaptor.getValue().getRole());
        assertNotNull(invite.getAcceptedAt());
    }

    @Test
    void updateReplacesRoles() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByKey("manager")).thenReturn(Optional.of(role("manager")));

        UpdateUserDto dto = new UpdateUserDto();
        dto.setRoleKeys(List.of("manager"));
        service.update(userId, dto);

        verify(userRoleRepository).deleteByUser_Id(userId);
        verify(userRoleRepository).save(any());
    }

    @Test
    void updateDoesNotTouchRolesWhenRoleKeysNull() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UpdateUserDto dto = new UpdateUserDto();
        dto.setName("Nuovo Nome");
        service.update(userId, dto);

        verify(userRoleRepository, never()).deleteByUser_Id(any());
        assertEquals("Nuovo Nome", user.getName());
    }

    @Test
    void deactivateRevokesActiveSessions() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Session s1 = new Session();
        Session s2 = new Session();
        when(sessionRepository.findByUser_IdAndRevokedAtIsNull(userId)).thenReturn(List.of(s1, s2));

        service.deactivate(userId);

        assertEquals(false, user.isActive());
        assertNotNull(s1.getRevokedAt());
        assertNotNull(s2.getRevokedAt());
        verify(sessionRepository).saveAll(List.of(s1, s2));
    }
}
