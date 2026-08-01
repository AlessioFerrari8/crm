package it.northleap.backend.services;

import it.northleap.backend.entities.Notification;
import it.northleap.backend.events.NotifyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Hub SSE per le notifiche realtime: un utente puo' avere piu' connessioni (piu' tab), ognuna un
// SseEmitter. push() invia l'evento "notification" a tutte le connessioni dell'utente; le
// connessioni morte vengono rimosse al primo invio fallito. Heartbeat periodico (commento SSE)
// per tenere vive le connessioni attraverso proxy/timeout intermedi.
// Le notifiche entrano da NotifyEvent (azione notify_user dei workflow), non da chiamate dirette.
// In-memory single-instance, stesso limite dichiarato del RateLimiter.
@Component
public class SseNotificationHub {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationHub.class);
    private static final long NO_TIMEOUT = 0L;
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> connections = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        connections.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError((e) -> remove(userId, emitter));
        return emitter;
    }

    public void push(UUID userId, Notification notification) {
        List<SseEmitter> emitters = connections.get(userId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    // commento SSE ogni 25s: mantiene vive le connessioni e fa emergere quelle morte
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = connections.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                connections.remove(userId, emitters);
            }
        }
    }

    // fallbackExecution: se non c'e' una transazione in corso l'evento va eseguito subito invece
    // di essere scartato in silenzio (default di @TransactionalEventListener)
    @TransactionalEventListener(fallbackExecution = true)
    public void onNotify(NotifyEvent nf) {
        push(nf.userId(), nf.notification());
    }
}
