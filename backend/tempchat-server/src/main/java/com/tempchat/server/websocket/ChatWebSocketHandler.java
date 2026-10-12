
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
import java.util.List;
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
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {

        if (session.getPrincipal() == null) {
            session.close(
                    CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized")
            );
            return;
        }

        String loginId = session.getPrincipal().getName();

        onlineUsers.put(loginId, session);

        // Send the current online-user list to this connection.
        sendPresenceSnapshot(session);

        // Tell all connected clients that this user is online.
        broadcastPresence(loginId, true);

        System.out.println("WebSocket connected: " + loginId);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        if (session.getPrincipal() == null) {
            session.close(
                    CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized")
            );
            return;
        }

        String sender = session.getPrincipal().getName();

        ChatMessage incoming = jsonMapper.readValue(
                message.getPayload(),
                ChatMessage.class
        );

        String receiver = incoming.getReceiver();
        String text = incoming.getMessage();

        if (receiver == null || receiver.isBlank()
                || text == null || text.isBlank()) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage(
                sender,
                receiver,
                text,
                Instant.now().toString()
        );

        chatService.saveMessage(chatMessage);

        WebSocketSession receiverSession = onlineUsers.get(receiver);

        if (receiverSession != null && receiverSession.isOpen()) {
            String response = jsonMapper.writeValueAsString(chatMessage);
            receiverSession.sendMessage(new TextMessage(response));
        }

        // Confirm the sent message to the sender.
        if (session.isOpen()) {
            String response = jsonMapper.writeValueAsString(chatMessage);
            session.sendMessage(new TextMessage(response));
        }
    }

    private void sendPresenceSnapshot(
            WebSocketSession session
    ) throws Exception {

        List<String> users = List.copyOf(onlineUsers.keySet());

        String payload = jsonMapper.writeValueAsString(
                Map.of(
                        "type", "presence_snapshot",
                        "users", users
                )
        );

        if (session.isOpen()) {
            session.sendMessage(new TextMessage(payload));
        }
    }

    private void broadcastPresence(
            String loginId,
            boolean online
    ) throws Exception {

        String payload = jsonMapper.writeValueAsString(
                Map.of(
                        "type", "presence",
                        "loginId", loginId,
                        "online", online
                )
        );

        TextMessage textMessage = new TextMessage(payload);

        for (WebSocketSession connectedSession : onlineUsers.values()) {
            if (connectedSession.isOpen()) {
                try {
                    connectedSession.sendMessage(textMessage);
                } catch (Exception exception) {
                    System.err.println(
                            "Could not send presence update: "
                                    + exception.getMessage()
                    );
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {

        if (session.getPrincipal() == null) {
            return;
        }

        String loginId = session.getPrincipal().getName();

        // Remove only this exact connection.
        boolean removed = onlineUsers.remove(loginId, session);

        if (removed) {
            try {
                broadcastPresence(loginId, false);
            } catch (Exception exception) {
                System.err.println(
                        "Could not broadcast offline status: "
                                + exception.getMessage()
                );
            }

            System.out.println("WebSocket disconnected: " + loginId);
        }
    }
}
