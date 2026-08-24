package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDashboardTabDto(
        @NotBlank @Size(max = 80) String label,
        Integer sortOrder
) {
}
