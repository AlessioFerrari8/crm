package it.northleap.backend.dtos;

public record StatusResponse(
    boolean onboarded,
    // true solo se il workspace esiste già e REGISTRATION_ENABLED è acceso
    boolean registrationOpen
) {
}
