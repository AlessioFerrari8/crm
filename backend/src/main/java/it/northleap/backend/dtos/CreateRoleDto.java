package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateRoleDto {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{1,64}$")
    private String key;

    @NotBlank
    private String label;
}
