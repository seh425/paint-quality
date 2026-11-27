package com.example.paintquality.user;

import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.common.RequireRoles;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequireRoles({Role.PRIMARY_ADMIN, Role.SECONDARY_ADMIN})
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/email")
    public UserResponse updateEmail(@CurrentUser InspectorUser user,
                                    @Valid @RequestBody EmailProfileRequest request) {
        return userService.updateOwnEmail(user.getId(), request.email());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> updatePassword(@CurrentUser InspectorUser user,
                                               @Valid @RequestBody PasswordProfileRequest request) {
        userService.updateOwnPassword(user.getId(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
