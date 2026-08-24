package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDashboardDto(
        @NotBlank @Size(max = 120) String name,
        boolean personal,
        List<String> roleIds, // vuoto/null = tutti i ruoli
        Integer sortOrder
) {
}
