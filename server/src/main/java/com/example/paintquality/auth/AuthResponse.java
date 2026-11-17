package com.example.paintquality.auth;

import com.example.paintquality.user.UserResponse;

public record AuthResponse(String token, UserResponse user) {
}
