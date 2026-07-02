package it.northleap.backend.services;

import it.northleap.backend.dtos.AcceptInviteDto;
import it.northleap.backend.dtos.AcceptedUserResponse;
import it.northleap.backend.dtos.InviteCreatedResponse;
import it.northleap.backend.dtos.InviteUserDto;
import it.northleap.backend.dtos.UpdateUserDto;
import it.northleap.backend.dtos.UserSummary;
import it.northleap.backend.entities.Invite;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.Session;
import it.northleap.backend.entities.User;
import it.northleap.backend.entities.UserRole;
import it.northleap.backend.entities.UserRoleId;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.NotFoundException;
import it.northleap.backend.repositories.InviteRepository;
import it.northleap.backend.repositories.RoleRepository;
import it.northleap.backend.repositories.SessionRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// Porting di UsersService (users.module.ts) - completa la Fase 1 (01-AUTH.md lista l'entity
// Invite ma non i suoi endpoint, gap segnalato in CLAUDE.md).
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InviteRepository inviteRepository;
    private final UserRoleRepository userRoleRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public List<UserSummary> list() {
        return userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getId(), u.getEmail(), u.getName(), u.getAvatarUrl(),
                        u.isActive(),
                        userRoleRepository.findByUser_Id(u.getId()).stream().map(ur -> ur.getRole().getKey()).toList(),
                        u.getCreatedAt()))
                .toList();
    }

    @Transactional
    public InviteCreatedResponse invite(Actor actor, InviteUserDto dto) {
        Role role = roleRepository.findByKey(dto.getRoleKey())
                .orElseThrow(() -> new BadRequestException("Ruolo inesistente"));
        String email = dto.getEmail().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Utente già esistente");
        }

        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        Invite invite = new Invite();
        invite.setEmail(email);
        invite.setRole(role);
        invite.setTokenHash(HashUtil.sha256Hex(token));
        if (actor != null) {
            invite.setInvitedBy(userRepository.getReferenceById(actor.id()));
        }
        invite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        inviteRepository.save(invite);

        auditService.log(actor, "invite", "user", email, null, null);
        return new InviteCreatedResponse(token, email);
    }

    @Transactional
    public AcceptedUserResponse accept(AcceptInviteDto dto) {
        Invite invite = inviteRepository.findByTokenHash(HashUtil.sha256Hex(dto.getToken())).orElse(null);
        if (invite == null || invite.getAcceptedAt() != null || invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Invito non valido o scaduto");
        }

        User user = new User();
        user.setEmail(invite.getEmail());
        user.setName(dto.getName());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setActive(true);
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), invite.getRole().getId()));
        userRole.setUser(user);
        userRole.setRole(invite.getRole());
        userRoleRepository.save(userRole);

        invite.setAcceptedAt(Instant.now());
        inviteRepository.save(invite);

        return new AcceptedUserResponse(user.getId(), user.getEmail());
    }

    @Transactional
    public void update(UUID id, UpdateUserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));

        if (dto.getRoleKeys() != null) {
            List<Role> roles = dto.getRoleKeys().stream()
                    .map(key -> roleRepository.findByKey(key)
                            .orElseThrow(() -> new BadRequestException("Ruolo inesistente: " + key)))
                    .toList();
            userRoleRepository.deleteByUser_Id(id);
            for (Role role : roles) {
                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(id, role.getId()));
                ur.setUser(user);
                ur.setRole(role);
                userRoleRepository.save(ur);
            }
        }
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getIsActive() != null) {
            user.setActive(dto.getIsActive());
        }
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
        user.setActive(false);
        userRepository.save(user);

        revokeActiveSessions(id);
    }

    // reset password lato admin (non c'è flusso self-service "password dimenticata" via email in
    // questo porting): imposta la nuova password e revoca le sessioni attive, così eventuali
    // refresh token in mano a terzi smettono di valere subito.
    @Transactional
    public void resetPassword(Actor actor, UUID id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        revokeActiveSessions(id);
        auditService.log(actor, "password_reset", "user", id.toString(), null, null);
    }

    private void revokeActiveSessions(UUID userId) {
        List<Session> sessions = sessionRepository.findByUser_IdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        sessions.forEach(s -> s.setRevokedAt(now));
        sessionRepository.saveAll(sessions);
    }
}
