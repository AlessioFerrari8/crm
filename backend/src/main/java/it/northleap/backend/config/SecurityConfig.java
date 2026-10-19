package it.northleap.backend.config;

import it.northleap.backend.security.CsrfCookieFilter;
import it.northleap.backend.security.JwtAuthenticationFilter;
import it.northleap.backend.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    // origini ammesse, angular di default. Non metto mai * qui
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    // config cors con vari permessi
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Api-Key", "X-Signature", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // oggetto per criptare la passwd
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    // gestisce problema csrf -> per richieste safe, non serve crsf
    // se usa x-api-key, probabilmente è rest o postman, quindi non sono vulnerabili a crsf
    // si escludono pure i webhooks, dato che sono già protetti
    private boolean requiresCsrfProtection(HttpServletRequest request) {
        Set<String> safeMethods = Set.of("GET", "HEAD", "TRACE", "OPTIONS");
        if (safeMethods.contains(request.getMethod())) {
            return false;
        }
        if (request.getHeader("X-Api-Key") != null) {
            return false;
        }
        // webhook esterni (generico + Cal.com) e form pubblici: chiamanti senza sessione browser
        // nostra (server o pagine embeddabili), protetti da firma HMAC / honeypot+rate-limit
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return !uri.startsWith(ctx + "/api/webhooks/in/")
                && !uri.startsWith(ctx + "/api/integrations/calcom/")
                && !uri.startsWith(ctx + "/api/public/");
    }

    // richieste http al login
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher csrfProtectionMatcher = this::requiresCsrfProtection;

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        // CookieCsrfTokenRepository.withHttpOnlyFalse(): il cookie XSRF-TOKEN
                        // ha bisongo di rimanere leggibile da TS; questo meccanismo double-submit: il frontend lo
                        // legge e lo rimanda come header X-XSRF-TOKEN, il server confronta i due.
                        // Angular HttpClient cerca esattamente questi nomi di default.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher)
                        // Di default Spring registra CsrfAuthenticationStrategy come
                        // SessionAuthenticationStrategy, che ruota/cancella il cookie XSRF-TOKEN
                        // "alla prima autenticazione di una sessione". In STATELESS pero' non
                        // esiste un vero SecurityContextRepository che persista lo stato tra
                        // richieste (e' un NullSecurityContextRepository) - quindi
                        // SessionManagementFilter.containsContext(request) e' SEMPRE false, e
                        // OGNI richiesta autenticata (es. ogni GET /me col cookie access_token)
                        // viene trattata come "nuovo login", cancellando XSRF-TOKEN ad ogni giro
                        // (trovato in smoke test live: il cookie spariva dopo la prima richiesta
                        // autenticata, rompendo qualunque richiesta mutante successiva). Non
                        // esistendo sessioni HTTP qui, non c'e' nessun rischio di session-fixation
                        // da mitigare: no-op esplicito.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error: il forward interno di Tomcat/Spring Boot per QUALSIASI
                        // risposta generata via HttpServletResponse.sendError() (compreso il
                        // default handling di Spring per MethodArgumentNotValidException sui
                        // @Valid falliti, o qualunque eccezione non gestita da un nostro
                        // @ExceptionHandler) passa da qui come una richiesta separata. Senza
                        // questo permitAll, anyRequest().authenticated() la nega con un 403
                        // vuoto che maschera il vero status (400/500/altro) - bug trovato in
                        // smoke test live, non specifico di nessun endpoint in particolare.
                        .requestMatchers("/error").permitAll()
                        // springdoc / Swagger UI: lo spec e la UI sono pubblici (solo in sviluppo
                        // tipicamente; in produzione valuta se proteggerli). Senza questo,
                        // anyRequest().authenticated() bloccherebbe il caricamento della UI.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/onboarding").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/resend-verification").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/mfa/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/accept-invite").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/in/*").permitAll()
                        // webhook Cal.com: pubblico, protetto dalla firma HMAC nel service (e
                        // spento del tutto finché CALCOM_WEBHOOK_SECRET non è configurato)
                        .requestMatchers(HttpMethod.POST, "/api/integrations/calcom/webhook").permitAll()
                        // form pubblici di lead capture: opt-in per tipo (publicForm=true),
                        // honeypot + rate limit per IP, 404 per i tipi non esposti
                        .requestMatchers("/api/public/forms/*").permitAll()
                        // campagne: pixel, click firmati, disiscrizione
                        .requestMatchers("/api/public/c/**").permitAll()
                        .requestMatchers("/api/public/nps/*").permitAll()
                        .requestMatchers("/api/public/quote/**").permitAll()
                        // portale clienti: per Spring Security è già pubblico, l'autenticazione la fa
                        // PortalAuthenticationFilter e ogni rotta pretende un PortalPrincipal (401 senza)
                        .requestMatchers("/api/portal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    // senza questo spring security nega con 403 generico
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"message\":\"Unauthorized\"}");
                }));



        return http.build();

    }
}
