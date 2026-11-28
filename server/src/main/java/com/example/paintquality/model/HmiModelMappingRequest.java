package com.example.paintquality.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HmiModelMappingRequest(
        @NotBlank(message = "HMI车型代码不能为空")
        @Size(max = 32, message = "HMI车型代码不能超过32个字符") String hmiValue,
        @NotBlank(message = "目标PVI车型代码不能为空")
        @Size(max = 32, message = "目标PVI车型代码不能超过32个字符") String pviCode) {
}
