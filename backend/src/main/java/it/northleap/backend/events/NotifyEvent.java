package it.northleap.backend.events;

import it.northleap.backend.entities.Notification;

import java.util.UUID;

// equivalente di events.emit('notify', ...) nell'originale (per il realtime via WebSocket);
// consumato da SseNotificationHub.onNotify, che lo spinge sulle connessioni SSE dell'utente
// a commit avvenuto.
public record NotifyEvent(UUID userId, Notification notification) {
}
