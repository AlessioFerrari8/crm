package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTagDto(
        @NotBlank @Size(max = 40) String name,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex value") String color,
        boolean isPrivate
) {

}