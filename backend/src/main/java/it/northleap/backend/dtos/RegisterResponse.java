package it.northleap.backend.dtos;

// corpo del 202 di /register: l'utente è creato ma deve ancora confermare l'email
public record RegisterResponse(boolean verificationRequired) {
}
