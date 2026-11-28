package com.example.paintquality.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "hmi_model_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_hmi_model_value", columnNames = "hmi_value"))
public class HmiModelMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hmi_value", nullable = false, length = 32)
    private String hmiValue;

    @Column(name = "pvi_code", nullable = false, length = 32)
    private String pviCode;

    protected HmiModelMapping() {
    }

    public HmiModelMapping(String hmiValue, String pviCode) {
        this.hmiValue = hmiValue;
        this.pviCode = pviCode;
    }

    public Long getId() {
        return id;
    }

    public String getHmiValue() {
        return hmiValue;
    }

    public void setHmiValue(String hmiValue) {
        this.hmiValue = hmiValue;
    }

    public String getPviCode() {
        return pviCode;
    }

    public void setPviCode(String pviCode) {
        this.pviCode = pviCode;
    }
}
