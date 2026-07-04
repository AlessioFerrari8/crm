package it.northleap.backend.controllers;

import it.northleap.backend.dtos.*;
import it.northleap.backend.security.AuthCookieService;
import it.northleap.backend.security.IssuedTokens;
import it.northleap.backend.security.UserPrincipal;
import it.northleap.backend.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return withTokenCookies(authService.login(request, httpRequest));
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

    private ResponseEntity<AuthResponse> withTokenCookies(IssuedTokens tokens) {
        ResponseCookie accessCookie = authCookieService.accessTokenCookie(tokens.accessToken());
        ResponseCookie refreshCookie = authCookieService.refreshTokenCookie(tokens.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.body());
    }
}
