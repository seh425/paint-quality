package com.example.paintquality.user;

public record UserResponse(Long id, String inspectorName, String area, String checkArea, Role role,
                           boolean enabled, String email) {

    public static UserResponse from(InspectorUser user) {
        return new UserResponse(user.getId(), user.getInspectorName(), user.getArea(),
                user.getCheckArea(), user.getRole(), user.isEnabled(), user.getEmail());
    }
}
