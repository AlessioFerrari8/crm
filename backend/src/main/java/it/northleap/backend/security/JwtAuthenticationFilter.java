package it.northleap.backend.security;

import it.northleap.backend.entities.ApiKey;
import it.northleap.backend.entities.User;
import it.northleap.backend.repositories.ApiKeyRepository;
import it.northleap.backend.repositories.UserRoleRepository;
import it.northleap.backend.services.HashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRoleRepository userRoleRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {

        // parte header auth + token. Ordine di precedenza: Authorization Bearer (client non
        // browser, es. Postman/script) -> cookie access_token (la SPA Angular, httpOnly quindi
        // mai letto da JS) -> X-Api-Key
        final String authHeader = request.getHeader("Authorization");
        final String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : readCookie(request, AuthCookieService.ACCESS_COOKIE_NAME);

        if (token == null) {
            // niente Bearer ne' cookie: prova X-Api-Key (02-RBAC.md - stessa estensione del
            // filtro descritta lì, l'Actor risolto da qui invece che dal JWT)
            String apiKeyHeader = request.getHeader("X-Api-Key");
            if (apiKeyHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateViaApiKey(request, apiKeyHeader);
            }
            filterChain.doFilter(request, response);
            return;
        }

        final String email;

        try {
            email = jwtService.extractEmail(token);
            // un token di sfida MFA non è un token di accesso: qui si ferma
            if ("mfa".equals(jwtService.extractType(token))) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception e) {
            // Token malformato, scaduto o firma non valida: lasciamo la richiesta
            // non autenticata, sarà Spring Security a rispondere 401/403 più avanti
            filterChain.doFilter(request, response);
            return;
        }

        // l'user è effettivamente autenticato
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Actor RBAC, separato da UserDetails: usato da RbacInterceptor/@CurrentActor
                if (userDetails instanceof UserPrincipal userPrincipal) {
                    User user = userPrincipal.getUser();
                    List<UUID> roleIds = userRoleRepository.findByUser_Id(user.getId()).stream()
                            .map(userRole -> userRole.getRole().getId())
                            .toList();
                    request.setAttribute(Actor.REQUEST_ATTRIBUTE,
                            new Actor(user.getId(), ActorType.USER, user.getEmail(), roleIds));
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Risolve un Actor (e soddisfa Spring Security's anyRequest().authenticated()) da una
    // ApiKey valida (non revocata, non scaduta). UserDetails/Actor restano concetti paralleli
    // (vedi 02-RBAC.md): qui non esiste uno User dietro la richiesta, quindi il principal di
    // Spring Security è minimale (solo per soddisfare l'autenticazione "tecnica"), mentre
    // l'Actor (type=APIKEY) è ciò che la logica RBAC custom usa davvero.
    private void authenticateViaApiKey(HttpServletRequest request, String rawKey) {
        ApiKey apiKey = apiKeyRepository.findByKeyHash(HashUtil.sha256Hex(rawKey)).orElse(null);
        if (apiKey == null || apiKey.getRevokedAt() != null
                || (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now()))) {
            return;
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                apiKey.getId().toString(), null, authorities
        );
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        List<UUID> roleIds = apiKey.getRole() != null ? List.of(apiKey.getRole().getId()) : List.of();
        request.setAttribute(Actor.REQUEST_ATTRIBUTE,
                new Actor(apiKey.getId(), ActorType.APIKEY, null, roleIds));

        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);
    }
}
