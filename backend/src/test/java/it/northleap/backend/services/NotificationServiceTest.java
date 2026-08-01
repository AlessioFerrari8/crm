package it.northleap.backend.services;

import it.northleap.backend.entities.Notification;
import it.northleap.backend.events.NotifyEvent;
import it.northleap.backend.repositories.NotificationRepository;
import it.northleap.backend.security.Actor;
import it.northleap.backend.security.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ApplicationEventPublisher events;

    private NotificationService service;
    private Actor actor;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, events);
        actor = new Actor(UUID.randomUUID(), ActorType.USER, "u@test.com", List.of());
    }

    @Test
    void markReadOnlyAffectsOwnNotification() {
        Notification mine = new Notification();
        mine.setId(UUID.randomUUID());
        mine.setUserId(actor.id());
        when(notificationRepository.findById(mine.getId())).thenReturn(Optional.of(mine));

        service.markRead(actor, mine.getId());

        assertNotNull(mine.getReadAt());
        verify(notificationRepository).save(mine);
    }

    @Test
    void markReadIsNoOpForSomeoneElsesNotification() {
        Notification someoneElses = new Notification();
        someoneElses.setId(UUID.randomUUID());
        someoneElses.setUserId(UUID.randomUUID());
        when(notificationRepository.findById(someoneElses.getId())).thenReturn(Optional.of(someoneElses));

        service.markRead(actor, someoneElses.getId());

        assertNull(someoneElses.getReadAt());
        verify(notificationRepository, never()).save(someoneElses);
    }

    @Test
    void markAllReadOnlyUpdatesUnreadOnes() {
        Notification unread1 = new Notification();
        Notification unread2 = new Notification();
        when(notificationRepository.findByUserIdAndReadAtIsNull(actor.id())).thenReturn(List.of(unread1, unread2));

        service.markAllRead(actor);

        assertNotNull(unread1.getReadAt());
        assertNotNull(unread2.getReadAt());
        verify(notificationRepository).saveAll(List.of(unread1, unread2));
    }

    // create() e' il punto unico di creazione (azione notify_user dei workflow inclusa): salva
    // e pubblica NotifyEvent, che SseNotificationHub trasforma nel push realtime a commit avvenuto
    @Test
    void createPersistsAndPublishesNotifyEvent() {
        UUID userId = UUID.randomUUID();

        Notification created = service.create(userId, "Titolo", "Corpo", "/records/x");

        assertEquals(userId, created.getUserId());
        assertEquals("Titolo", created.getTitle());
        assertEquals("Corpo", created.getBody());
        assertEquals("/records/x", created.getLink());
        verify(notificationRepository).save(created);
        verify(events).publishEvent(new NotifyEvent(userId, created));
    }
}
