package it.northleap.backend.exceptions;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// @Order(0): vedi nota in GlobalExceptionAdvice - deve avere precedenza sul catch-all
@RestControllerAdvice
@Order(0)
public class RecordExceptionAdvice {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 404,
                        "message", ex.getMessage()
                )
        );
    }

    @ExceptionHandler(RecordValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(RecordValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", 400,
                        "message", ex.getMessage()
                )
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 400);
        body.put("message", ex.getMessage());
        // il codice è opzionale, solo gli errori che il frontend deve tradurre ce l'hanno
        if (ex.getCode() != null) {
            body.put("code", ex.getCode());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

}
