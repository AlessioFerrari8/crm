package it.northleap.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name required")
        @Size(max = 100, message = "Name too long")
        String name,

        @NotBlank(message = "Email required")
        @Email(message = "Email format not valid")
        @Size(max = 254, message = "Email too long")
        String email,

        // il minimo vero lo applica PasswordPolicy.assertValid in AuthService; il tetto evita che bcrypt lavori su
        // stringhe enormi (è la prima rotta pubblica che crea utenti)
        @NotBlank(message = "Password required")
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        String password

) {
}
