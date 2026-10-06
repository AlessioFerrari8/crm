package it.northleap.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CampaignDto(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 200) String preheader,
        @NotBlank String bodyHtml,
        @NotBlank String bodyText,
        @NotBlank String targetObjectKey,
        FilterGroup targetFilter) {
}
