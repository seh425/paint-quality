package com.example.paintquality.auth;

import com.example.paintquality.AppProperties;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import com.example.paintquality.user.UserRepository;
import com.example.paintquality.user.UserResponse;
import com.example.paintquality.user.UserValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, AuthTokenRepository tokenRepository,
                       AppProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String name = UserValues.name(request.inspectorName());
        String area = UserValues.area(request.area());
        if (userRepository.existsByInspectorNameAndArea(name, area)) {
            throw ApiException.conflict("该姓名和区域已注册");
        }

        InspectorUser user = new InspectorUser(name, area, Role.USER, true);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("该姓名和区域已注册");
        }
        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String name = UserValues.name(request.inspectorName());
        String area = UserValues.area(request.area());
        InspectorUser user = userRepository.findByInspectorNameAndArea(name, area)
                .orElseThrow(() -> ApiException.unauthorized("检查人姓名或区域不正确"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("该用户已被禁用");
        }
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public InspectorUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("缺少Bearer token");
        }
        AuthToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Bearer token无效"));
        if (!token.getExpiresAt().isAfter(Instant.now(clock))) {
            throw ApiException.unauthorized("Bearer token已过期");
        }
        InspectorUser user = token.getUser();
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("该用户已被禁用");
        }
        return user;
    }

    @Transactional
    public void logout(String rawToken) {
        tokenRepository.deleteByTokenHash(hash(rawToken));
    }

    private AuthResponse issueToken(InspectorUser user) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = Instant.now(clock).plus(properties.getTokenValidityDays(), ChronoUnit.DAYS);
        tokenRepository.save(new AuthToken(hash(rawToken), user, expiresAt));
        return new AuthResponse(rawToken, UserResponse.from(user));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
}
