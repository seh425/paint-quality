package com.example.paintquality.record;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record RecordBatchRequest(
        @NotNull LocalDate workDate,
        @NotNull @Min(1) @Max(5) Integer roundNumber,
        @NotNull @Min(1) @Max(2) Integer lineNumber,
        @NotNull @Size(min = 5, max = 5) List<@NotNull @Valid VehicleInput> vehicles) {
}
