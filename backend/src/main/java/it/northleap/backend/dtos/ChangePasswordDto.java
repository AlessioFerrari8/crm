package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordDto {
    @NotBlank
    private String currentPassword;

    // la robustezza la verifica PasswordPolicy nel service (messaggio puntuale sulle regole mancanti)
    @NotBlank
    private String newPassword;
}
