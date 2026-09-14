package it.northleap.backend.dtos;

import jakarta.validation.constraints.*;

public record PdfTemplateDto(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9_-]+", message = "Only lowercase letters, digits, dash and underscore")
        String key,
        @NotBlank @Size(max = 120) String label,
        @NotBlank @Size(max = 64) String objectKey,
        @NotNull @Size(max = 200_000) String html,
        @Size(max = 16) String pageSize,
        @Min(0) @Max(60) Integer marginMm
) { }
