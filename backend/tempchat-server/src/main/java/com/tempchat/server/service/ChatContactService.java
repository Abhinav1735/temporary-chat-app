
package com.tempchat.server.service;

import com.tempchat.server.dto.UserResponse;
import com.tempchat.server.entity.ChatContact;
import com.tempchat.server.entity.Role;
import com.tempchat.server.entity.User;
import com.tempchat.server.repository.ChatContactRepository;
import com.tempchat.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatContactService {

    private final ChatContactRepository contactRepository;
    private final UserRepository userRepository;

    public ChatContactService(
            ChatContactRepository contactRepository,
            UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getContacts(String currentLoginId) {
        return contactRepository
                .findByOwner_LoginIdOrderByCreatedAtDesc(currentLoginId)
                .stream()
                .map(ChatContact::getContact)
                .filter(User::isEnabled)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(
            String currentLoginId,
            String query) {

        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String search = query.trim().toLowerCase();

        return userRepository.findAll()
                .stream()
                .filter(User::isEnabled)
                .filter(user -> user.getRole() == Role.USER)
                .filter(user ->
                        !user.getLoginId().equalsIgnoreCase(currentLoginId))
                .filter(user ->
                        user.getLoginId().toLowerCase().contains(search))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse addContact(
            String currentLoginId,
            Long contactId) {

        User owner = userRepository.findByLoginId(currentLoginId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Current user not found"));

        User contact = userRepository.findById(contactId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        if (!contact.isEnabled() || contact.getRole() != Role.USER) {
            throw new IllegalArgumentException(
                    "This user is not available to add");
        }

        if (owner.getId().equals(contact.getId())) {
            throw new IllegalArgumentException(
                    "You cannot add yourself");
        }

        if (contactRepository.existsByOwner_IdAndContact_Id(
                owner.getId(), contact.getId())) {
            throw new IllegalArgumentException(
                    "User is already added to your chats");
        }

        contactRepository.save(new ChatContact(owner, contact));

        return toResponse(contact);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getRole(),
                user.isMustChangeCredentials(),
                user.isEnabled()
        );
    }
}
