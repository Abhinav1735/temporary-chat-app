package com.tempchat.server.service;

import java.util.List;
import com.tempchat.server.dto.CreateUserRequest;
import com.tempchat.server.dto.UserResponse;
import com.tempchat.server.entity.Role;
import com.tempchat.server.entity.User;
import com.tempchat.server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tempchat.server.dto.ChangeCredentialsRequest;
import com.tempchat.server.dto.ResetPasswordRequest;
import com.tempchat.server.dto.UpdateUserStatusRequest;
import org.springframework.security.core.Authentication;

import com.tempchat.server.dto.ChangeCredentialsResponse;
import com.tempchat.server.security.JwtService;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        String loginId = request.getLoginId().trim();

        if (userRepository.existsByLoginId(loginId)) {
            throw new IllegalArgumentException(
                    "Login ID already exists"
            );
        }

        User user = new User(
                loginId,
                passwordEncoder.encode(request.getPassword()),
                Role.USER
        );

        // New users must change their temporary credentials
        // on their first successful login.
        user.setMustChangeCredentials(true);

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getLoginId(),
                savedUser.getRole(),
                savedUser.isMustChangeCredentials(),
                savedUser.isEnabled()
        );
    }

    @Transactional
    public ChangeCredentialsResponse changeCredentials(
            Authentication authentication,
            ChangeCredentialsRequest request) {

        String currentLoginId = authentication.getName();

        User user = userRepository.findByLoginId(currentLoginId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        // Verify current password
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect"
            );
        }

        String newLoginId = request.getNewLoginId().trim();

        // Check whether the new Login ID is already in use
        if (!newLoginId.equals(user.getLoginId())
                && userRepository.existsByLoginId(newLoginId)) {

            throw new IllegalArgumentException(
                    "New login ID already exists"
            );
        }

        // Update Login ID
        user.setLoginId(newLoginId);

        // Hash and update password
        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        // First-login requirement completed
        user.setMustChangeCredentials(false);

        User savedUser = userRepository.save(user);

        // Generate a new JWT using the NEW Login ID
        String newToken = jwtService.generateToken(
                savedUser.getLoginId(),
                savedUser.getRole().name()
        );

        return new ChangeCredentialsResponse(
                newToken,
                savedUser.getLoginId(),
                savedUser.getRole().name(),
                savedUser.isMustChangeCredentials()
        );
    }

    @Transactional
    public UserResponse resetUserPassword(
            Long userId,
            ResetPasswordRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        // Force the user to change this temporary password
        user.setMustChangeCredentials(true);

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getLoginId(),
                savedUser.getRole(),
                savedUser.isMustChangeCredentials(),
                savedUser.isEnabled()
        );
    }

    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getLoginId(),
                        user.getRole(),
                        user.isMustChangeCredentials(),
                        user.isEnabled()
                ))
                .toList();
    }

    @Transactional
    public UserResponse updateUserStatus(
            Long userId,
            UpdateUserStatusRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        user.setEnabled(request.getEnabled());

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getLoginId(),
                savedUser.getRole(),
                savedUser.isMustChangeCredentials(),
                savedUser.isEnabled()
        );
    }


    public List<UserResponse> getChatUsers(String currentLoginId) {
        return userRepository.findAll()
                .stream()
                .filter(User::isEnabled)
                .filter(user ->
                        !user.getLoginId().equals(currentLoginId))
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getLoginId(),
                        user.getRole(),
                        user.isMustChangeCredentials(),
                        user.isEnabled()
                ))
                .toList();
    }


    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "Admin accounts cannot be deleted here"
            );
        }

        userRepository.delete(user);
    }


}