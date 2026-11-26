package com.example.paintquality.excel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "area_templates", uniqueConstraints =
        @UniqueConstraint(name = "uk_area_template_area", columnNames = {"area"}))
public class AreaTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String area;

    @Lob
    @Column(name = "content_base64", nullable = false)
    private String contentBase64;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AreaTemplate() {
    }

    public AreaTemplate(String area, String contentBase64, Instant updatedAt) {
        this.area = area;
        this.contentBase64 = contentBase64;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getArea() {
        return area;
    }

    public String getContentBase64() {
        return contentBase64;
    }

    public void setContentBase64(String contentBase64) {
        this.contentBase64 = contentBase64;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
