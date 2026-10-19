package it.northleap.backend.services;

import it.northleap.backend.dtos.*;
import it.northleap.backend.entities.Role;
import it.northleap.backend.entities.Session;
import it.northleap.backend.entities.User;
import it.northleap.backend.entities.UserRole;
import it.northleap.backend.entities.UserRoleId;
import it.northleap.backend.entities.Workspace;
import it.northleap.backend.exceptions.*;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    // ruolo assegnato a chi si registra da solo (seminato da RoleSeeder)
    private static final String SELF_REGISTER_ROLE = "member";

    private static SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFICATION_HOURS = 24;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;

    private final EmailService emailService;

    // origin del frontend: finisce nel link della mail (stessa proprietà di UserService)
    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    // non final, come registrationEnabled: in AuthServiceTest resta false, quindi i test della registrazione che non
    // toccano la mail continuano a funzionare senza stub; Spring lo inietta da env (default true)
    @Value("${app.registration.require-email-verification:true}")
    private boolean requireEmailVerification;

    // non final di proposito: @RequiredArgsConstructor non lo mette nel costruttore, quindi AuthServiceTest continua
    // a costruire il service con 9 argomenti; Spring lo inietta da env
    @Value("${app.registration.enabled:false}")
    private boolean registrationEnabled;

    public StatusResponse status() {
        boolean onboarded = isOnboarded();
        return new StatusResponse(onboarded, onboarded && registrationEnabled);
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

    public record RegisterOutcome(IssuedTokens tokens) { }

    // registrazione pubblica: crea un utente "member" nel workspace che esiste già. Non crea un workspace nuovo (il modello
    // dati ne ha uno solo): chi si registra vde gli stessi dati
    @Transactional
    public RegisterOutcome register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (!registrationEnabled) {
            throw new RegistrationClosedException();
        }
        if (!isOnboarded()) {
            throw new BadRequestException("Workspace non ancora configurato: completa prima l'onboarding");
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Utente già esistente", ErrorCodes.EMAIL_TAKEN);
        }
        PasswordPolicy.assertValid(request.password());

        // fail closed: senza posta funzionante nessuno potrebbe mai confermare, meglio rifiutare subito
        if (requireEmailVerification && !emailService.isConfigured()) {
            throw new BadRequestException("Registrazione temporaneamente non disponibile: invio email non configurato");
        }

        Role role = roleRepository.findByKey(SELF_REGISTER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role '" + SELF_REGISTER_ROLE + "' not seeded"));

        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setEmailVerified(!requireEmailVerification);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // due registrazioni con la stessa email nello stesso istante: vince la prima
            throw new BadRequestException("Utente già esistente", ErrorCodes.EMAIL_TAKEN);
        }

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        if (requireEmailVerification) {
            // se l'invio fallisce l'eccezione fa il rollback: l'utente non resta a meta'
            if (!sendVerificationEmail(user)) {
                throw new BadRequestException("Impossibile inviare l'email di conferma: riprova tra poco");
            }
            return new RegisterOutcome(null);
        }
        return new RegisterOutcome(issueTokens(user, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()));
    }

    // conferma l'indirizzo: il token si cerca per hash, scade e si consuma
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByVerificationTokenHash(HashUtil.sha256Hex(request.token()))
                .filter(u -> u.getVerificationExpiresAt() != null && u.getVerificationExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Link non valido o scaduto", ErrorCodes.VERIFICATION_INVALID));
        user.setEmailVerified(true);
        user.setVerificationTokenHash(null);
        user.setVerificationExpiresAt(null);
        userRepository.save(user);
    }

    // rinvia il link. Non dice mai se l'email esiste o è già verificata (stessa risposta per tutti): il token
    // precedente viene sostituito
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        if (!requireEmailVerification || !emailService.isConfigured()) {
            return;
        }
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> !u.isEmailVerified())
                // pausa di 60 secondi: la scadenza e' "invio + 24h", quindi se mancano ancora quasi 24h
                // l'ultima mail e' di meno di un minuto fa
                .filter(u -> u.getVerificationExpiresAt() == null
                        || u.getVerificationExpiresAt().isBefore(
                        Instant.now().plus(VERIFICATION_HOURS, ChronoUnit.HOURS).minusSeconds(60)))
                .ifPresent(this::sendVerificationEmail);
    }

    // genera un token nuovo, ne salva l'hash e manda il link. false = la mail non e' partita
    private boolean sendVerificationEmail(User user) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        user.setVerificationTokenHash(HashUtil.sha256Hex(token));
        user.setVerificationExpiresAt(Instant.now().plus(VERIFICATION_HOURS, ChronoUnit.HOURS));
        userRepository.save(user);

        String link = frontendBaseUrl + "/verify?token=" + token;
        // il nome lo ha scelto chi si registra: in una mail HTML va escapato
        String html = """
                <p>Ciao %s,</p>
                <p>conferma il tuo indirizzo email per entrare in NorthLeap CRM:</p>
                <p><a href="%s">Conferma l'email</a></p>
                <p>Il link scade tra %d ore. Se non sei stato tu a registrarti, ignora questa email.</p>
                """.formatted(HtmlUtils.htmlEscape(user.getName()), link, VERIFICATION_HOURS);
        return emailService.send(user.getEmail(), "Conferma il tuo indirizzo email · NorthLeap CRM", html);
    }


    public record LoginOutcome(IssuedTokens tokens, String mfaToken) { }

    public LoginOutcome login(LoginRequest request, HttpServletRequest httpRequest) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        // password giusta, ma indirizzo non confermato: nessun token, nemmeno la sfida MFA
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

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
