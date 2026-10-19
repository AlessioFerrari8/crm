package it.northleap.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank(message = "Email required")
        @Email(message = "Email format not valid")
        @Size(max = 254, message = "Email too long")
        String email
) {
}
