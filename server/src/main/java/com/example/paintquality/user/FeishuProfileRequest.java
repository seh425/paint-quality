package com.example.paintquality.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeishuProfileRequest(@NotBlank @Size(max = 128) String feishuOpenId) {
}
