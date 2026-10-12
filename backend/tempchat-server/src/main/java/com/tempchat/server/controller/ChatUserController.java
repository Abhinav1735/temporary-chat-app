
package com.tempchat.server.controller;

import com.tempchat.server.dto.UserResponse;
import com.tempchat.server.service.ChatContactService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class ChatUserController {

    private final ChatContactService chatContactService;

    public ChatUserController(ChatContactService chatContactService) {
        this.chatContactService = chatContactService;
    }

    @GetMapping("/chat-list")
    public ResponseEntity<List<UserResponse>> getChatUsers(
            Authentication authentication) {

        return ResponseEntity.ok(
                chatContactService.getContacts(authentication.getName())
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam("q") String query,
            Authentication authentication) {

        return ResponseEntity.ok(
                chatContactService.searchUsers(
                        authentication.getName(),
                        query
                )
        );
    }

    @PostMapping("/chat-list/{userId}")
    public ResponseEntity<?> addContact(
            @PathVariable Long userId,
            Authentication authentication) {

        try {
            UserResponse added = chatContactService.addContact(
                    authentication.getName(),
                    userId
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(added);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
