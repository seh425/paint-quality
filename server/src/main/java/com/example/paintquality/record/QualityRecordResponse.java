package com.example.paintquality.record;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record QualityRecordResponse(
        Long id,
        LocalDate workDate,
        int roundNumber,
        int lineNumber,
        int vehiclePosition,
        String vin,
        String vehicleNumber,
        String modelCode,
        String vehicleModel,
        InspectionStatus status,
        String issueNote,
        String inspectorName,
        String area,
        String checkArea,
        LocalDateTime createdAt) {

    public static QualityRecordResponse from(QualityRecord record) {
        return new QualityRecordResponse(record.getId(), record.getWorkDate(), record.getRoundNumber(),
                record.getLineNumber(), record.getVehiclePosition(), record.getVin(),
                record.getVehicleNumber(), record.getModelCode(), record.getVehicleModel(),
                record.getStatus(), record.getIssueNote(), record.getInspectorName(),
                record.getArea(), record.getCheckArea(), record.getCreatedAt());
    }
}
