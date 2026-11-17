package com.example.paintquality.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleModelRequest(@NotBlank @Size(max = 32) String code,
                                  @NotBlank @Size(max = 100) String modelName) {
}
