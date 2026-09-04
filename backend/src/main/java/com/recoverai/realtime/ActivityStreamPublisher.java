package com.recoverai.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory fan-out of ActivityEvents to every connected browser.
 *
 * Two rules this class lives by:
 *  1. publish() must NEVER throw into business logic. A dead browser tab is not a
 *     reason to fail a payment recovery — every send is individually guarded.
 *  2. A late subscriber still sees the last REPLAY_BUFFER_SIZE events, so opening
 *     the dashboard mid-batch doesn't show an empty feed.
 */
@Slf4j
@Component
public class ActivityStreamPublisher {

    /** Long enough for a demo, short enough that a forgotten tab doesn't pin a thread forever. */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int REPLAY_BUFFER_SIZE = 100;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<ActivityEvent> recent = new ArrayDeque<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> emitters.remove(emitter));

        emitters.add(emitter);

        try {
            emitter.send(SseEmitter.event().name("connected").data("activity stream open"));
            for (ActivityEvent event : snapshot()) {
                emitter.send(SseEmitter.event()
                        .id(event.id())
                        .name("activity")
                        .data(event, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException ex) {
            log.debug("SSE subscriber dropped during replay: {}", ex.getMessage());
            drop(emitter);
        }

        return emitter;
    }

    public void publish(ActivityEvent event) {
        if (event == null) {
            return;
        }
        remember(event);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.id())
                        .name("activity")
                        .data(event, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                // Browser closed the tab mid-batch. Not an application error.
                drop(emitter);
            }
        }
    }

    /** Keeps proxies and idle connections from silently killing the stream between batches. */
    @Scheduled(fixedRate = 20_000L)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception ex) {
                drop(emitter);
            }
        }
    }

    public int subscriberCount() {
        return emitters.size();
    }

    private void drop(SseEmitter emitter) {
        emitters.remove(emitter);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already dead
        }
    }

    private void remember(ActivityEvent event) {
        synchronized (recent) {
            recent.addLast(event);
            while (recent.size() > REPLAY_BUFFER_SIZE) {
                recent.removeFirst();
            }
        }
    }

    private List<ActivityEvent> snapshot() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }
}