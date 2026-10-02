package com.tempchat.server.service;

import com.tempchat.server.dto.LoginRequest;
import com.tempchat.server.dto.LoginResponse;
import com.tempchat.server.entity.User;
import com.tempchat.server.repository.UserRepository;
import com.tempchat.server.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        String loginId = request.getLoginId().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginId,
                        request.getPassword()
                )
        );

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() ->
                        new IllegalStateException("User not found")
                );

        String token = jwtService.generateToken(
                user.getLoginId(),
                user.getRole().name()
        );

        return new LoginResponse(
                token,
                user.getLoginId(),
                user.getRole().name(),
                user.isMustChangeCredentials()
        );
    }
}