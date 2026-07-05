package it.northleap.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Applica RateLimiter agli endpoint più esposti a brute-force/spam: login, refresh,
// accept-invite, form pubblici, webhook Cal.com e cambio password (quest'ultimo è PATCH
// autenticato: senza limite, una sessione rubata può forzare la password attuale a tentativi).
// Non qui: onboarding (si auto-blocca dopo il primo uso), status (sola lettura), webhook
// generico inbound (già protetto dalla verifica HMAC).
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_POST_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/users/accept-invite",
            "/api/integrations/calcom/webhook"
    );

    private final RateLimiter rateLimiter;

    private boolean isLimited(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if ("POST".equalsIgnoreCase(method)) {
            // form pubblici: path dinamico, match per prefisso
            return LIMITED_POST_PATHS.contains(path) || path.startsWith("/api/public/forms/");
        }
        // brute-force della password attuale via cambio password self-service
        return "PATCH".equalsIgnoreCase(method) && "/api/auth/password".equals(path);
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getServletPath() + ":" + request.getRemoteAddr();
        if (!rateLimiter.allow(key)) {
            // 429: non presente tra le costanti SC_* di HttpServletResponse (mai aggiunta
            // all'API servlet), valore letterale RFC 6585
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Troppe richieste, riprova più tardi\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
