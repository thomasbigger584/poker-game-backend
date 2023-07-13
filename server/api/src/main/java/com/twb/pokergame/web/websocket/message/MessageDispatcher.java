package com.twb.pokergame.web.websocket.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twb.pokergame.web.websocket.message.server.ServerMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageDispatcher {
    private static final String TOPIC = "/topic/loops.%s";

    private final SimpMessagingTemplate template;
    private final ObjectMapper objectMapper;

    public void send(String pokerTableId, ServerMessage message) {
        try {
            String destination = String.format(TOPIC, pokerTableId);
            template.convertAndSend(destination, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
