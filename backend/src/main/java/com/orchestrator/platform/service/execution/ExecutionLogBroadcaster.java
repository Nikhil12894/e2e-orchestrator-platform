package com.orchestrator.platform.service.execution;

import com.orchestrator.platform.domain.enums.ExecutionStatus;
import com.orchestrator.platform.domain.model.dto.ExecutionEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExecutionLogBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLogBroadcaster.class);

    private final Optional<SimpMessagingTemplate> messagingTemplate;
    private final Map<UUID, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
    private final Map<UUID, List<ExecutionEventDto>> eventHistory = new ConcurrentHashMap<>();

    public ExecutionLogBroadcaster(Optional<SimpMessagingTemplate> messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(ExecutionEventDto event) {
        UUID executionId = event.getExecutionId();
        log.info("[Execution {}] {} - {}: {}", executionId, event.getEventType(), event.getStepName(), event.getMessage());

        // Cache event in history (keep last 500 events per execution)
        if (executionId != null) {
            eventHistory.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(event);
        }

        // 1. WebSocket broadcast
        messagingTemplate.ifPresent(template -> {
            try {
                template.convertAndSend("/topic/executions", event);
                if (executionId != null) {
                    template.convertAndSend("/topic/executions/" + executionId, event);
                }
            } catch (Exception e) {
                log.warn("Failed to broadcast event via WebSocket: {}", e.getMessage());
            }
        });

        // 2. SSE broadcast
        if (executionId != null && sseEmitters.containsKey(executionId)) {
            List<SseEmitter> emitters = sseEmitters.get(executionId);
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("execution-event")
                            .data(event));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    public void emitLog(UUID executionId, UUID workflowId, String stepId, String stepName, String message) {
        broadcast(new ExecutionEventDto(executionId, workflowId, "LOG_MESSAGE", stepId, stepName,
                ExecutionStatus.RUNNING, message, null));
    }

    public SseEmitter registerSseEmitter(UUID executionId) {
        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout
        sseEmitters.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(executionId, emitter));
        emitter.onTimeout(() -> removeEmitter(executionId, emitter));
        emitter.onError(e -> removeEmitter(executionId, emitter));

        // Replay existing events to the newly connected emitter
        List<ExecutionEventDto> history = eventHistory.get(executionId);
        if (history != null) {
            for (ExecutionEventDto historicalEvent : history) {
                try {
                    emitter.send(SseEmitter.event().name("execution-event").data(historicalEvent));
                } catch (IOException ignored) {
                    break;
                }
            }
        }

        return emitter;
    }

    private void removeEmitter(UUID executionId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseEmitters.get(executionId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                sseEmitters.remove(executionId);
            }
        }
    }

    public List<ExecutionEventDto> getHistory(UUID executionId) {
        return eventHistory.getOrDefault(executionId, Collections.emptyList());
    }
}
