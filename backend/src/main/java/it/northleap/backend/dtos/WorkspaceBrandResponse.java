package it.northleap.backend.dtos;

// proiezione del workspace per i settings / la topbar - solo i campi "di brand" pubblici,
// niente onboarded/timestamp interni.
public record WorkspaceBrandResponse(String name, String brandColor, String logoUrl, String calcomUrl) {
}
