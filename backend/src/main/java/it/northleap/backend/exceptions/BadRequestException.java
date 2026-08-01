package it.northleap.backend.exceptions;

// 400 generico, message-carrying, riusabile fuori dal contesto specifico della validazione Record
// (es. ObjectsService: chiave già esistente, object type di sistema non eliminabile)
public class BadRequestException extends RuntimeException {
    private final String code; // se null nessun codice

    public BadRequestException(String message) { this(message, null); }
    public BadRequestException(String message, String code) { super(message); this.code = code; }
    public String getCode() { return code; }
}
