package com.example.paintquality.record;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleInput(
        @NotNull @Min(1) @Max(5) Integer vehiclePosition,
        @NotNull @Size(max = 64) String vin,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$") String vehicleNumber,
        @NotBlank @Size(max = 32) String modelCode,
        @NotBlank @Size(max = 100) String vehicleModel,
        @NotNull InspectionStatus status,
        @Size(max = 1000) String issueNote) {
}
