package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        // 24 byte in esadecimale = 48 caratteri; il tetto evita di fare hash di stringhe enormi
        @NotBlank(message = "Token required")
        @Size(max = 128, message = "Token too long")
        String token
) {
}
