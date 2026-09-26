package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;

public record MfaDisableDto(@NotBlank String password) {
}
