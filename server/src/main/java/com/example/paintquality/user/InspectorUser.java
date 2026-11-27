package com.example.paintquality.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "inspector_users", uniqueConstraints =
        @UniqueConstraint(name = "uk_inspector_name_area", columnNames = {"inspector_name", "area"}))
public class InspectorUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspector_name", nullable = false, length = 80)
    private String inspectorName;

    @Column(nullable = false, length = 50)
    private String area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "feishu_open_id", length = 128)
    private String feishuOpenId;

    @Column(name = "check_area", length = 50)
    private String checkArea;

    @Column(length = 128)
    private String email;

    @Column(name = "password_hash", length = 200)
    private String passwordHash;

    @Column(name = "deleted_flag")
    private Boolean deleted = false;

    protected InspectorUser() {
    }

    public InspectorUser(String inspectorName, String area, Role role, boolean enabled) {
        this.inspectorName = inspectorName;
        this.area = area;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFeishuOpenId() {
        return feishuOpenId;
    }

    public void setFeishuOpenId(String feishuOpenId) {
        this.feishuOpenId = feishuOpenId;
    }

    public String getCheckArea() {
        return checkArea;
    }

    public void setCheckArea(String checkArea) {
        this.checkArea = checkArea;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
