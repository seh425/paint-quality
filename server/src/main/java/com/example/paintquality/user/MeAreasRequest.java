package com.example.paintquality.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeAreasRequest(@NotBlank @Size(max = 50) String area,
                             @NotBlank @Size(max = 50) String checkArea) {
}
