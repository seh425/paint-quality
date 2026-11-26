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
import java.util.List;
import java.util.Optional;

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
        boolean exists = userRepository.findAllByInspectorName(name).stream()
                .anyMatch(user -> !user.isDeleted());
        if (exists) {
            throw ApiException.conflict("该检查人姓名已注册");
        }

        InspectorUser user = new InspectorUser(name, "", Role.USER, false);
        user.setCheckArea(UserValues.checkArea(request.checkArea()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("该检查人姓名已注册");
        }
        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String name = UserValues.name(request.inspectorName());
        String area = UserValues.area(request.area());
        String checkArea = UserValues.checkArea(request.checkArea());
        List<InspectorUser> candidates = userRepository.findAllByInspectorName(name).stream()
                .filter(user -> !user.isDeleted())
                .toList();
        if (candidates.isEmpty()) {
            throw ApiException.unauthorized("检查人姓名未注册");
        }
        InspectorUser user;
        if (candidates.size() == 1) {
            user = candidates.get(0);
        } else {
            user = candidates.stream()
                    .filter(candidate -> area.equals(candidate.getArea()))
                    .findFirst()
                    .or(() -> checkArea == null ? Optional.empty() : candidates.stream()
                            .filter(candidate -> checkArea.equals(candidate.getCheckArea()))
                            .findFirst())
                    .orElseThrow(() -> ApiException.unauthorized("存在同名账号，请联系管理员"));
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("账号尚未通过管理员审核或已被禁用");
        }
        user.setArea(area);
        if (checkArea != null) {
            user.setCheckArea(checkArea);
        }
        userRepository.save(user);
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
