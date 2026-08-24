package it.northleap.backend.dtos;

import it.northleap.backend.entities.DashboardWidget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateWidgetDto(
        @NotBlank String label,
        @NotNull DashboardWidget.WidgetKind kind,
        String color,
        String icon,
        @Min(1) @Max(4) Integer gridSpan,
        Integer sortOrder,
        @NotNull Map<String, Object> query
) {
}
