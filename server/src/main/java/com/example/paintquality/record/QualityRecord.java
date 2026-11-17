package com.example.paintquality.record;

import com.example.paintquality.user.InspectorUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "quality_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_quality_record_slot",
                columnNames = {"work_date", "round_number", "line_number", "vehicle_position"}),
        indexes = @Index(name = "idx_quality_record_work_date", columnList = "work_date"))
public class QualityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "vehicle_position", nullable = false)
    private int vehiclePosition;

    @Column(nullable = false, length = 64)
    private String vin;

    @Column(name = "vehicle_number", nullable = false, length = 6)
    private String vehicleNumber;

    @Column(name = "model_code", nullable = false, length = 32)
    private String modelCode;

    @Column(name = "vehicle_model", nullable = false, length = 100)
    private String vehicleModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private InspectionStatus status;

    @Column(name = "issue_note", length = 1000)
    private String issueNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspector_id", nullable = false)
    private InspectorUser inspector;

    @Column(name = "inspector_name", nullable = false, length = 80)
    private String inspectorName;

    @Column(nullable = false, length = 50)
    private String area;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected QualityRecord() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getVehiclePosition() {
        return vehiclePosition;
    }

    public void setVehiclePosition(int vehiclePosition) {
        this.vehiclePosition = vehiclePosition;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public InspectionStatus getStatus() {
        return status;
    }

    public void setStatus(InspectionStatus status) {
        this.status = status;
    }

    public String getIssueNote() {
        return issueNote;
    }

    public void setIssueNote(String issueNote) {
        this.issueNote = issueNote;
    }

    public InspectorUser getInspector() {
        return inspector;
    }

    public void setInspector(InspectorUser inspector) {
        this.inspector = inspector;
    }

    public String getInspectorName() {
        return inspectorName;
    }

    public void setInspectorName(String inspectorName) {
        this.inspectorName = inspectorName;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
