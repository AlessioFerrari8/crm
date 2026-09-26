package it.northleap.backend.dtos;

public record MfaChallengeResponse(boolean mfaRequired, String mfaToken) {
}
