package com.example.paintquality.auth;

import com.example.paintquality.common.CurrentUser;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.MeAreasRequest;
import com.example.paintquality.user.UserResponse;
import com.example.paintquality.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(authorization.substring("Bearer ".length()).trim());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser InspectorUser user) {
        return UserResponse.from(user);
    }

    @PatchMapping("/me/areas")
    public UserResponse updateAreas(@CurrentUser InspectorUser user,
                                    @Valid @RequestBody MeAreasRequest request) {
        return userService.updateOwnAreas(user.getId(), request.area(), request.checkArea());
    }
}
