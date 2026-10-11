package it.northleap.backend.portal;


import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// popola un PortalPrincipal, mai un Actor e mail il SecurityContext. Gira solo sotto /api/portal
@Component
@RequiredArgsConstructor
public class PortalAuthenticationFilter extends OncePerRequestFilter {

    private final PortalTokenService tokens;
    private final PortalUserRepository users;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/portal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String token = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) if (PortalTokenService.COOKIE.equals(c.getName())) token = c.getValue();
        }
        if (token != null) {
            try {
                Claims c = tokens.parse(token);
                // l'utente si ricarica a ogni richiesta: disattivato o password cambiata = token morto subito
                users.findById(UUID.fromString(c.getSubject()))
                        .filter(PortalUser::isActive)
                        .filter(u -> u.getTokensValidAfter() == null || !c.getIssuedAt().toInstant().isBefore(
                                u.getTokensValidAfter().minusSeconds(1)))
                        .ifPresent(u -> req.setAttribute(PortalPrincipal.REQUEST_ATTRIBUTE, new PortalPrincipal(u.getId(), u.getContactRecordId(), u.getEmail())));
            } catch (Exception ignored) {
                // token scaduto, firma sbagliata, token interno: nessun principal, l'endpoint risponde 401
            }
        }
        chain.doFilter(req, res);
    }
}
