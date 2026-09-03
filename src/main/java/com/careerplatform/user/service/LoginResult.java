package com.careerplatform.user.service;

import com.careerplatform.user.enums.UserStatus;

public record LoginResult(
        String token,
        Long userId,
        String username,
        UserStatus status) {
}
