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

// Applica RateLimiter ai 3 endpoint pubblici più esposti a brute-force/spam: login, refresh,
// accept-invite. Tutti gli altri endpoint pubblici (status, onboarding, webhook inbound) non
// sono qui - onboarding si auto-blocca dopo il primo uso, status è sola lettura senza side
// effect, webhook inbound è già protetto dalla verifica HMAC.
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/users/accept-invite"
    );

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {
        boolean limited = LIMITED_PATHS.contains(request.getServletPath())
                // form pubblici di lead capture: path dinamico, match per prefisso
                || request.getServletPath().startsWith("/api/public/forms/");
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !limited) {
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
