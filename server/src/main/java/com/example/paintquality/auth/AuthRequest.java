package com.example.paintquality.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(@NotBlank @Size(max = 80) String inspectorName,
                          @NotBlank @Size(max = 50) String area) {
}
