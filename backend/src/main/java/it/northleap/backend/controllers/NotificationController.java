package it.northleap.backend.controllers;

import it.northleap.backend.entities.Notification;
import it.northleap.backend.exceptions.BadRequestException;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import it.northleap.backend.security.CurrentActor;
import it.northleap.backend.services.NotificationService;
import it.northleap.backend.services.SseNotificationHub;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

// Porting di NotificationsController (notifications.module.ts). Niente @RequirePerm: sempre
// personali all'utente autenticato, coperte da anyRequest().authenticated().
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseNotificationHub sseNotificationHub;

    @GetMapping
    public ResponseEntity<List<Notification>> list(@CurrentActor Actor actor) {
        return ResponseEntity.ok(notificationService.list(actor));
    }

    // per una API key Actor.id() è l'id della chiave, non di un utente: registrare una
    // connessione su quell'id la terrebbe aperta a vuoto per sempre, senza mai un destinatario
    // (gli altri metodi qui non hanno la guardia perché chiudono subito, questo no)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentActor Actor actor) {
        if (actor.type() != ActorType.USER) {
            throw new BadRequestException("This endpoint requires a JWT-authenticated user, not an API key");
        }
        return sseNotificationHub.register(actor.id());
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(@CurrentActor Actor actor) {
        notificationService.markAllRead(actor);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> read(@CurrentActor Actor actor, @PathVariable UUID id) {
        notificationService.markRead(actor, id);
        return ResponseEntity.noContent().build();
    }
}
