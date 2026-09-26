package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;

public record MfaCodeDto(@NotBlank String code) {
}
