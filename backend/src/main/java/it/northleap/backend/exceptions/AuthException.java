package it.northleap.backend.exceptions;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// @Order(0): deve essere valutata prima del catch-all in GlobalExceptionAdvice (vedi il commento lì per il bug che
// questa precedenza esplicita previene - senza @Order, beans senza l'annotazione finiscono comunque a
// LOWEST_PRECEDENCE come il catch-all, e l'ordine fra loro non è garantito)
@RestControllerAdvice
@Order(0)
public class AuthException {

    // gestione con credenziali non valide e ritorno di status non autorizzato
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredential(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 401,
                        "message", "Invalid credentials"
                )
        );
    }

    // per accessi da evitare e cosi via
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 403,
                        "message", "User disabled"
                )
        );
    }

    // onboarding già completato, non si può rifare il primo utente
    @ExceptionHandler(WorkspaceAlreadyOnboardedException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceAlreadyOnboarded(WorkspaceAlreadyOnboardedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 400,
                        "message", ex.getMessage()
                )
        );
    }

    // registrazione pubblica spenta (REGISTRATION_ENABLED=false)
    @ExceptionHandler(RegistrationClosedException.class)
    public ResponseEntity<Map<String, Object>> handleRegistrationClosed(RegistrationClosedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 403,
                        "message", ex.getMessage()
                )
        );
    }

    // password giusta ma email non confermata: il frontend legge il code per offrire "Rinvia email"
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 403,
                        "message", ex.getMessage(),
                        "code", ErrorCodes.EMAIL_NOT_VERIFIED
                )
        );
    }

    // refresh token mancante/scaduto/revocato: stesso messaggio generico, non si specifica il motivo
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 401,
                        "message", ex.getMessage()
                )
        );
    }
}
