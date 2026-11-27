package com.example.paintquality.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordProfileRequest(@NotBlank @Size(max = 100) String newPassword) {
}
