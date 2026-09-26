package it.northleap.backend.services;

import it.northleap.backend.dtos.*;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.Session;
import it.northleap.backend.entities.User;
import it.northleap.backend.entities.UserRole;
import it.northleap.backend.entities.UserRoleId;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.exceptions.ErrorCodes;
import it.northleap.backend.exceptions.InvalidRefreshTokenException;
import it.northleap.backend.exceptions.WorkspaceAlreadyOnboardedException;
import it.northleap.backend.repositories.RoleRepository;
import it.northleap.backend.repositories.SessionRepository;
import it.northleap.backend.repositories.UserRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.repositories.WorkspaceRepository;
import it.northleap.backend.security.IssuedTokens;
import it.northleap.backend.security.JwtService;
import it.northleap.backend.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;

    public StatusResponse status() {
        return new StatusResponse(isOnboarded());
    }

    @Transactional
    public IssuedTokens onboarding(OnboardingRequest request, HttpServletRequest httpRequest) {
        if (isOnboarded()) {
            throw new WorkspaceAlreadyOnboardedException();
        }

        Workspace workspace = new Workspace();
        workspace.setName(request.workspaceName());
        workspace.setOnboarded(true);
        workspaceRepository.save(workspace);

        User user = new User();
        user.setEmail(request.email());
        user.setName(request.name());
        PasswordPolicy.assertValid(request.password());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        userRepository.save(user);

        Role ownerRole = roleRepository.findByKey("owner")
                .orElseThrow(() -> new IllegalStateException("Role 'owner' not seeded"));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), ownerRole.getId()));
        userRole.setUser(user);
        userRole.setRole(ownerRole);
        userRoleRepository.save(userRole);

        return issueTokens(user, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
    }

    public record LoginOutcome(IssuedTokens tokens, String mfaToken) { }

    public LoginOutcome login(LoginRequest request, HttpServletRequest httpRequest) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        // la password è giusta, ma da sola non basta più
        if (user.isMfaEnabled()) {
            return new LoginOutcome(null, jwtService.generateMfaToken(user));
        }

        return new LoginOutcome(
                issueTokens(user, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()), null);
    }

    // qui nascono i cookie e la Session
    @Transactional
    public IssuedTokens verifyMfa(MfaVerifyDto dto, HttpServletRequest httpServletRequest) {
        UUID userId;
        try {
            userId = jwtService.extractUserIdFromMfaToken(dto.mfaToken());
        } catch (Exception e) {
            // scaduto, manomesso o di tipo sbagliato: un solo messaggio
            throw new BadRequestException("Invalid or expired MFA challenge");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Invalid or expired MFA challenge"));

        mfaService.assertSecondFactor(user, dto.code(), dto.recoveryCode());

        return issueTokens(user, httpServletRequest.getHeader("User-Agent"),
                httpServletRequest.getRemoteAddr());
    }

    @Transactional
    public IssuedTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String userId;
        try {
            userId = jwtService.extractUserIdFromRefresh(refreshToken);
        } catch (Exception e) {
            throw new InvalidRefreshTokenException();
        }

        String hash = jwtService.hashToken(refreshToken);
        Session session = sessionRepository.findByRefreshHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(InvalidRefreshTokenException::new);

        // rotation: il refresh consuma la sessione precedente, mitiga replay
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);

        return issueTokens(user, session.getUserAgent(), session.getIp());
    }

    @Transactional
    public void logout(String refreshToken) {
        // niente cookie -> niente sessione da revocare, no-op silenzioso (stesso trattamento di
        // NotificationService.markRead per un id che non combacia: non e' un errore del client)
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String hash = jwtService.hashToken(refreshToken);
        sessionRepository.findByRefreshHash(hash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                sessionRepository.save(session);
            }
        });
    }

    // cambio password self-service: verifica la password attuale, applica la policy, revoca
    // tutte le sessioni TRANNE quella corrente (individuata dal cookie refresh, che viaggia su
    // questo path /api/auth) - eventuali sessioni rubate/altrui muoiono, l'utente resta loggato
    @Transactional
    public void changePassword(UserPrincipal principal, String currentPassword, String newPassword, String refreshToken) {
        if (principal == null) {
            throw new BadRequestException("Questo endpoint richiede un utente autenticato via JWT, non una API key");
        }
        User user = principal.getUser();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("La password attuale non è corretta", ErrorCodes.CURRENT_PASSWORD_INVALID);
        }
        PasswordPolicy.assertValid(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        String keepHash = refreshToken != null ? jwtService.hashToken(refreshToken) : null;
        Instant now = Instant.now();
        List<Session> sessions = sessionRepository.findByUser_IdAndRevokedAtIsNull(user.getId());
        for (Session session : sessions) {
            if (keepHash == null || !keepHash.equals(session.getRefreshHash())) {
                session.setRevokedAt(now);
            }
        }
        sessionRepository.saveAll(sessions);
    }

    public MeResponse me(UserPrincipal principal) {
        // principal e' null quando la richiesta e' autenticata via X-Api-Key invece che JWT
        // (Actor != UserPrincipal, vedi 02-RBAC.md) - @AuthenticationPrincipal non lancia da
        // solo se il tipo non combacia, torna null silenziosamente; senza questo controllo
        // principal.getUser() sotto andava in NullPointerException non gestita (trovato in
        // smoke test live, risultava in un 500 invece di un 400 pulito)
        if (principal == null) {
            throw new BadRequestException("Questo endpoint richiede un utente autenticato via JWT, non una API key");
        }
        User user = principal.getUser();
        List<String> roles = userRoleRepository.findByUser_Id(user.getId()).stream()
                .map(userRole -> userRole.getRole().getKey())
                .toList();
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl(), roles, user.isMfaEnabled());
    }

    private boolean isOnboarded() {
        return workspaceRepository.findTopByOrderByCreatedAtAsc()
                .map(Workspace::isOnboarded)
                .orElse(false);
    }

    private IssuedTokens issueTokens(User user, String userAgent, String ip) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(user);

        Session session = new Session();
        session.setUser(user);
        session.setRefreshHash(jwtService.hashToken(refreshToken));
        session.setUserAgent(userAgent);
        session.setIp(ip);
        session.setExpiresAt(jwtService.extractRefreshExpiration(refreshToken).toInstant());
        sessionRepository.save(session);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        AuthResponse body = new AuthResponse(user.getId(), user.getEmail(), user.getName());
        return new IssuedTokens(accessToken, refreshToken, body);
    }
}
