package com.careerplatform.user.dto;

import com.careerplatform.user.enums.UserStatus;

public class RegisterResponse {

    private final Long id;

    private final String username;

    private final UserStatus status;

    public RegisterResponse(Long id, String username, UserStatus status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public UserStatus getStatus() {
        return status;
    }
}
