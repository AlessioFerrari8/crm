package it.northleap.backend.services;

import it.northleap.backend.entities.Notification;
import it.northleap.backend.repositories.NotificationRepository;
import it.northleap.backend.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import it.northleap.backend.events.NotifyEvent;
import org.springframework.context.ApplicationEventPublisher;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Porting di NotificationsService (notifications.module.ts). Frontend farà polling
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher events;

    public List<Notification> list(Actor actor) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(actor.id());
    }

    // stesso comportamento "silenzioso" dell'originale (Prisma updateMany su id+userId): se la
    // notifica non esiste o non è dell'utente, no-op, nessun errore sollevato
    @Transactional
    public void markRead(Actor actor, UUID id) {
        notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(actor.id()))
                .ifPresent(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public void markAllRead(Actor actor) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadAtIsNull(actor.id());
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public Notification create(UUID userId, String title, String body, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notificationRepository.save(notification);
        events.publishEvent(new NotifyEvent(userId, notification));
        return notification;
    }
}
