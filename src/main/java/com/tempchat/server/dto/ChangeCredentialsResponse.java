package com.tempchat.server.dto;

public class ChangeCredentialsResponse {

    private String token;
    private String loginId;
    private String role;
    private boolean mustChangeCredentials;

    public ChangeCredentialsResponse(
            String token,
            String loginId,
            String role,
            boolean mustChangeCredentials) {

        this.token = token;
        this.loginId = loginId;
        this.role = role;
        this.mustChangeCredentials = mustChangeCredentials;
    }

    public String getToken() {
        return token;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getRole() {
        return role;
    }

    public boolean isMustChangeCredentials() {
        return mustChangeCredentials;
    }
}