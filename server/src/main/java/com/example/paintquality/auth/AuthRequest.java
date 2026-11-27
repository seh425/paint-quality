package com.example.paintquality.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(@NotBlank @Size(max = 80) String inspectorName,
                          @Size(max = 50) String area,
                          @Size(max = 50) String checkArea,
                          @Size(max = 100) String password) {
}
