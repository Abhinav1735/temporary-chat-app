package com.tempchat.server.websocket;

import com.tempchat.server.dto.ChatMessage;
import com.tempchat.server.service.ChatService;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final JsonMapper jsonMapper;

    private final Map<String, WebSocketSession> onlineUsers =
            new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ChatService chatService,
            JsonMapper jsonMapper
    ) {
        this.chatService = chatService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }

        String loginId = session.getPrincipal().getName();

        onlineUsers.put(loginId, session);

        System.out.println("WebSocket connected: " + loginId);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }

        String sender = session.getPrincipal().getName();

        ChatMessage incoming =
                jsonMapper.readValue(message.getPayload(), ChatMessage.class);

        String receiver = incoming.getReceiver();
        String text = incoming.getMessage();

        if (receiver == null || receiver.isBlank()) {
            return;
        }

        if (text == null || text.isBlank()) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage(
                sender,
                receiver,
                text,
                Instant.now().toString()
        );

        chatService.saveMessage(chatMessage);

        WebSocketSession receiverSession =
                onlineUsers.get(receiver);

        if (receiverSession != null && receiverSession.isOpen()) {

            String response =
                    jsonMapper.writeValueAsString(chatMessage);

            receiverSession.sendMessage(
                    new TextMessage(response)
            );
        }

        // Send the message back to the sender as confirmation
        if (session.isOpen()) {

            String response =
                    jsonMapper.writeValueAsString(chatMessage);

            session.sendMessage(
                    new TextMessage(response)
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {

        if (session.getPrincipal() != null) {

            String loginId = session.getPrincipal().getName();

            onlineUsers.remove(loginId, session);

            System.out.println(
                    "WebSocket disconnected: " + loginId
            );
        }
    }
}