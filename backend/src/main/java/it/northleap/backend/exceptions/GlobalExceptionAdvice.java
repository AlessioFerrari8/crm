package it.northleap.backend.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

// Rete di sicurezza globale, valida su ogni controller. Due scopi:
// 1. MethodArgumentNotValidException (fallimento di un @Valid su un body) non ha mai avuto un
//    @ExceptionHandler dedicato in questo progetto - cadeva sul default Spring Boot, che passa
//    da HttpServletResponse.sendError() e quindi dal forward interno /error (vedi il permitAll
//    su /error in SecurityConfig, stesso bug, due sintomi). Qui restituiamo un 400 con i
//    messaggi di validazione per-campo, stessa shape {timestamp,status,message} già usata dalle
//    altre advice del progetto.
// 2. Un catch-all su Exception, ultima rete di sicurezza: qualunque eccezione non gestita da
//    nessun'altra advice più specifica torna comunque un 500 pulito con quella stessa shape,
//    invece di affidarsi silenziosamente al comportamento di default di Spring Boot.
//
// @Order(LOWEST_PRECEDENCE): bug trovato in smoke test live - Spring valuta le @ControllerAdvice
// in ordine di bean (non per specificità del tipo di eccezione) e usa la PRIMA che ha un
// handler applicabile, fermandosi lì. Senza questa precedenza più bassa, il catch-all
// Exception.class qui sotto intercettava anche BadRequestException/NotFoundException/ecc.
// PRIMA che le advice più specifiche (RecordExceptionAdvice, AuthException, RbacExceptionAdvice)
// avessero la possibilità di gestirle, trasformando dei 400/404/403 attesi in 500 generici.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 400,
                        "message", message.isEmpty() ? "Richiesta non valida" : message
                )
        );
    }

    // 401 del portale, 410 del link NPS già usato: senza questo handler li prenderebbe quello generico qui sotto
    // e diventerebbero 500
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", ex.getStatusCode().value(),
                "message", ex.getReason() == null ? "" : ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Eccezione non gestita da nessuna advice più specifica", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 500,
                        "message", "Errore interno"
                )
        );
    }
}
