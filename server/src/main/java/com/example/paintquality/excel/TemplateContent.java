package com.example.paintquality.excel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 当前使用的Excel模板。模板保存在数据库中，避免云平台临时磁盘重启后丢失管理员上传的模板。
 */
@Entity
@Table(name = "excel_template")
public class TemplateContent {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TemplateContent() {
    }

    public TemplateContent(Long id, byte[] content, LocalDateTime updatedAt) {
        this.id = id;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
