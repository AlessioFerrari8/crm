package it.northleap.backend.controllers;

import it.northleap.backend.dtos.PublicFormResponse;
import it.northleap.backend.dtos.PublicFormSubmission;
import it.northleap.backend.services.PublicFormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Modulo pubblico di lead capture. Pubblico (permitAll + esente CSRF in SecurityConfig) e
// rate-limited per IP (RateLimitFilter, prefisso /api/public/forms/). La pagina che lo rende
// e' /f/{key} nel frontend, condivisibile o incorporabile via iframe.
@RestController
@RequestMapping("/api/public/forms")
@RequiredArgsConstructor
public class PublicFormController {

    private final PublicFormService publicFormService;

    @GetMapping("/{key}")
    public ResponseEntity<PublicFormResponse> definition(@PathVariable String key) {
        return ResponseEntity.ok(publicFormService.definition(key));
    }

    @PostMapping("/{key}")
    public ResponseEntity<Map<String, Object>> submit(
            @PathVariable String key,
            @Valid @RequestBody PublicFormSubmission submission
    ) {
        return ResponseEntity.ok(publicFormService.submit(key, submission));
    }
}
