package com.example.paintquality.user;

public record UserResponse(Long id, String inspectorName, String area, Role role,
                           boolean enabled, String feishuOpenId) {

    public static UserResponse from(InspectorUser user) {
        return new UserResponse(user.getId(), user.getInspectorName(), user.getArea(),
                user.getRole(), user.isEnabled(), user.getFeishuOpenId());
    }
}
