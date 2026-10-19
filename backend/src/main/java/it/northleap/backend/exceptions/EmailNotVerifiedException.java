package it.northleap.backend.exceptions;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email non ancora verificata");
    }
}
