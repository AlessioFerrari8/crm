package it.northleap.backend.security;

public enum ActorType {
    USER,
    APIKEY,
    // cliente del portal: usato SOLO per l'audit. Nessun ruolo, quindi RbacService gli nega tutto anche se un
    // giorno finisse per sbaglio davanti a un service interno
    PORTAL
}
