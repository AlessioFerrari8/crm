package it.northleap.backend.dtos;

import java.util.List;
import java.util.UUID;

public record MeResponse(
    UUID userId,
    String email,
    String name,
    String avatarUrl,
    List<String> roles,
    boolean mfaEnabled
) {
}
