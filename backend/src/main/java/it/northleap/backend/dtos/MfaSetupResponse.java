package it.northleap.backend.dtos;

public record MfaSetupResponse(String secretBase32, String qrPng) {
}
