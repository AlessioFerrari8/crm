package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;

// il login a due passi: uno dei due fra code e recoveryCode
public record MfaVerifyDto(@NotBlank String mfaToken, String code, String recoveryCode) {
}
