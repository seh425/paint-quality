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

    public static String feishuOpenId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw ApiException.badRequest("飞书Open ID不能超过128个字符");
        }
        return normalized;
    }
}
