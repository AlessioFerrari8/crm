package it.northleap.backend.exceptions;

public class RegistrationClosedException extends RuntimeException {
    public RegistrationClosedException() {
        super("Registrazione non disponibile");
    }
}
