package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class CreateSavedViewDto {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{1,64}$")
    private String objectKey;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Map<String, Object> config;
}
