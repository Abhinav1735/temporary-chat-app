package com.tempchat.server.controller;

import com.tempchat.server.dto.ChangeCredentialsRequest;
import com.tempchat.server.dto.LoginRequest;
import com.tempchat.server.dto.LoginResponse;

import com.tempchat.server.service.AuthService;
import com.tempchat.server.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.tempchat.server.dto.ChangeCredentialsResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(
            AuthService authService,
            UserService userService) {

        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PutMapping("/change-credentials")
    public ResponseEntity<ChangeCredentialsResponse> changeCredentials(
            @Valid @RequestBody ChangeCredentialsRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                userService.changeCredentials(
                        authentication,
                        request
                )
        );
    }
}