package com.careerplatform.user.dto;

import com.careerplatform.user.enums.UserStatus;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        UserStatus status) {
}
