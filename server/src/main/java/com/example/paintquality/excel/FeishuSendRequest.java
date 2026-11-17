package com.example.paintquality.excel;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FeishuSendRequest(@NotNull LocalDate date) {
}
