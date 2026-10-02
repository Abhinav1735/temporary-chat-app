package com.tempchat.server.service;

import com.tempchat.server.dto.ChatMessage;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private final RedisService redisService;
    private final JsonMapper jsonMapper;

    // Fallback expiration: 24 hours
    private static final long CHAT_EXPIRATION_SECONDS = 24 * 60 * 60;

    public ChatService(
            RedisService redisService,
            JsonMapper jsonMapper) {

        this.redisService = redisService;
        this.jsonMapper = jsonMapper;
    }

    public void saveMessage(ChatMessage chatMessage) {

        String key = buildChatKey(
                chatMessage.getSender(),
                chatMessage.getReceiver()
        );

        try {

            String existingMessages = redisService.get(key);

            List<ChatMessage> messages;

            if (existingMessages == null || existingMessages.isBlank()) {

                messages = new ArrayList<>();

            } else {

                messages = jsonMapper.readValue(
                        existingMessages,
                        jsonMapper.getTypeFactory()
                                .constructCollectionType(
                                        List.class,
                                        ChatMessage.class
                                )
                );
            }

            messages.add(chatMessage);

            String updatedMessages =
                    jsonMapper.writeValueAsString(messages);

            redisService.set(
                    key,
                    updatedMessages,
                    CHAT_EXPIRATION_SECONDS
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to save chat message",
                    e
            );
        }
    }

    public List<ChatMessage> getMessages(
            String user1,
            String user2) {

        String key = buildChatKey(user1, user2);

        String storedMessages = redisService.get(key);

        if (storedMessages == null || storedMessages.isBlank()) {
            return new ArrayList<>();
        }

        try {

            return jsonMapper.readValue(
                    storedMessages,
                    jsonMapper.getTypeFactory()
                            .constructCollectionType(
                                    List.class,
                                    ChatMessage.class
                            )
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to read chat messages",
                    e
            );
        }
    }

    private String buildChatKey(
            String user1,
            String user2) {

        if (user1.compareTo(user2) < 0) {
            return "chat:" + user1 + ":" + user2;
        }

        return "chat:" + user2 + ":" + user1;
    }

    @EventListener(ContextClosedEvent.class)
    public void clearAllChats() {

        System.out.println("Clearing temporary chat data...");

        redisService.deleteByPattern("chat:*");

        System.out.println("Temporary chat data cleared.");
    }
}