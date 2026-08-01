package it.northleap.backend.exceptions;

// I codici che il frontend traduce. Stringhe qui e non enum perche' viaggiano in JSON e
// devono restare stabili anche se un domani il nome Java cambia.
public final class ErrorCodes {
    public static final String PASSWORD_TOO_WEAK = "PASSWORD_TOO_WEAK";
    public static final String INVITE_INVALID = "INVITE_INVALID";
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";

    private ErrorCodes() {}
}