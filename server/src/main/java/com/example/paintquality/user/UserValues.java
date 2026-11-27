package com.example.paintquality.user;

import com.example.paintquality.common.ApiException;

import java.util.Locale;

public final class UserValues {

    private UserValues() {
    }

    public static String name(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("检查人姓名不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 80) {
            throw ApiException.badRequest("检查人姓名不能超过80个字符");
        }
        return normalized;
    }

    public static String area(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("区域不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 50) {
            throw ApiException.badRequest("区域不能超过50个字符");
        }
        return normalized;
    }

    public static String checkArea(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 50) {
            throw ApiException.badRequest("检查区域不能超过50个字符");
        }
        return normalized;
    }

    public static String password(String value) {
        if (value == null || value.isEmpty()) {
            throw ApiException.badRequest("登录密码不能为空");
        }
        if (value.length() < 6) {
            throw ApiException.badRequest("登录密码至少6位");
        }
        if (value.length() > 100) {
            throw ApiException.badRequest("登录密码不能超过100个字符");
        }
        return value;
    }

    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw ApiException.badRequest("邮箱不能超过128个字符");
        }
        if (!normalized.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")) {
            throw ApiException.badRequest("邮箱格式不正确");
        }
        return normalized;
    }
}
