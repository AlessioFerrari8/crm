package it.northleap.backend.controllers;

import it.northleap.backend.dtos.*;
import it.northleap.backend.security.AuthCookieService;
import it.northleap.backend.security.IssuedTokens;
import it.northleap.backend.security.UserPrincipal;
import it.northleap.backend.services.AuthService;
import it.northleap.backend.services.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final MfaService mfaService;

    // dice se il workspace è già stato configurato
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(authService.status());
    }

    // crea il primo workspace + utente owner, si blocca da solo dopo il primo uso
    @PostMapping("/onboarding")
    public ResponseEntity<AuthResponse> onboarding(@Valid @RequestBody OnboardingRequest request, HttpServletRequest httpRequest) {
        return withTokenCookies(authService.onboarding(request, httpRequest));
    }

    // metodo login
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.LoginOutcome outcome = authService.login(request, httpRequest);
        if (outcome.mfaToken() != null) {
            // 200 con la sfida: nessun Set-Cookie, nessuna Session creata
            return ResponseEntity.ok(new MfaChallengeResponse(true, outcome.mfaToken()));
        }
        return ResponseEntity.ok()
                .headers(cookieHeaders(outcome.tokens()))
                .body(outcome.tokens().body());
    }

    // pubblico: arriva senza cookie
    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody MfaVerifyDto dto, HttpServletRequest httpServletRequest) {
        return withTokenCookies(authService.verifyMfa(dto, httpServletRequest));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(mfaService.setup(userPrincipal.getUser().getId()));
    }

    // i dieci codici di recupero si vedono qui, una volta sola
    @PostMapping("/mfa/activate")
    public ResponseEntity<List<String>> activateMfa(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                    @Valid @RequestBody MfaCodeDto dto) {
        return ResponseEntity.ok(mfaService.activate(userPrincipal.getUser().getId(), dto.code()));
    }

    @DeleteMapping("/mfa")
    public ResponseEntity<Void> disableMfa(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                           @Valid @RequestBody MfaDisableDto dto) {
        mfaService.disable(userPrincipal.getUser().getId(), dto.password());
        return ResponseEntity.noContent().build();
    }

    // rinnova access+refresh token, ruotando la sessione - il refresh token arriva dal cookie,
    // mai dal body (vedi AuthCookieService)
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        return withTokenCookies(authService.refresh(refreshToken));
    }

    // revoca la sessione associata al refresh token nel cookie, poi cancella entrambi i cookie
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshTokenCookie().toString())
                .build();
    }

    // utente corrente con i suoi ruoli
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.me(principal));
    }

    // cambio password self-service (richiede la password attuale); la sessione corrente resta
    // viva (individuata dal cookie refresh), le altre vengono revocate
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            @Valid @RequestBody ChangePasswordDto dto
    ) {
        authService.changePassword(principal, dto.getCurrentPassword(), dto.getNewPassword(), refreshToken);
        return ResponseEntity.noContent().build();
    }

    private HttpHeaders cookieHeaders(IssuedTokens tokens) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.SET_COOKIE, authCookieService.accessTokenCookie(tokens.accessToken()).toString());
        h.add(HttpHeaders.SET_COOKIE, authCookieService.refreshTokenCookie(tokens.refreshToken()).toString());
        return h;
    }

    private ResponseEntity<AuthResponse> withTokenCookies(IssuedTokens tokens) {
        return ResponseEntity.ok().headers(cookieHeaders(tokens)).body(tokens.body());
    }

}
