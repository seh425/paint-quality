package com.example.paintquality.model;

public record VehicleModelResponse(Long id, String code, String modelName, boolean active) {

    public static VehicleModelResponse from(VehicleModel model) {
        return new VehicleModelResponse(model.getId(), model.getCode(), model.getModelName(), model.isActive());
    }
}
