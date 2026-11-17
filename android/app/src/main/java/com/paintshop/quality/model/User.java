package com.paintshop.quality.model;

public class User {
    public long id;
    public String inspectorName = "";
    public String area = "";
    public String role = "USER";
    public boolean enabled = true;
    public String feishuOpenId = "";

    public boolean isAdmin() {
        return "PRIMARY_ADMIN".equals(role) || "SECONDARY_ADMIN".equals(role);
    }

    public boolean isPrimaryAdmin() {
        return "PRIMARY_ADMIN".equals(role);
    }
}
