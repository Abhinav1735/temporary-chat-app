package com.tempchat.server.dto;

import com.tempchat.server.entity.Role;

public class UserResponse {

    private Long id;
    private String loginId;
    private Role role;
    private boolean mustChangeCredentials;
    private boolean enabled;

    public UserResponse() {
    }

    public UserResponse(
            Long id,
            String loginId,
            Role role,
            boolean mustChangeCredentials,
            boolean enabled) {

        this.id = id;
        this.loginId = loginId;
        this.role = role;
        this.mustChangeCredentials = mustChangeCredentials;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isMustChangeCredentials() {
        return mustChangeCredentials;
    }

    public boolean isEnabled() {
        return enabled;
    }
}