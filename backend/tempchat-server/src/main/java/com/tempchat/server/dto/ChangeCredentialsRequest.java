package com.tempchat.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangeCredentialsRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New login ID is required")
    @Size(
            min = 4,
            max = 50,
            message = "New login ID must be between 4 and 50 characters"
    )
    private String newLoginId;

    @NotBlank(message = "New password is required")
    @Size(
            min = 8,
            max = 100,
            message = "New password must be at least 8 characters"
    )
    private String newPassword;

    public ChangeCredentialsRequest() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewLoginId() {
        return newLoginId;
    }

    public void setNewLoginId(String newLoginId) {
        this.newLoginId = newLoginId;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}