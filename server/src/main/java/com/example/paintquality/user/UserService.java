package com.example.paintquality.user;

import com.example.paintquality.auth.AuthTokenRepository;
import com.example.paintquality.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuthTokenRepository tokenRepository;

    public UserService(UserRepository userRepository, AuthTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        InspectorUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        if (user.getRole() == Role.PRIMARY_ADMIN
                && request.role() != null && request.role() != Role.PRIMARY_ADMIN) {
            throw ApiException.conflict("一级管理员不可降级");
        }
        if (user.getRole() == Role.PRIMARY_ADMIN && Boolean.FALSE.equals(request.enabled())) {
            throw ApiException.conflict("一级管理员不可禁用");
        }
        if (request.role() == Role.PRIMARY_ADMIN && user.getRole() != Role.PRIMARY_ADMIN
                && userRepository.countByRole(Role.PRIMARY_ADMIN) > 0) {
            throw ApiException.conflict("一级管理员只能有1人");
        }
        if (request.role() == Role.SECONDARY_ADMIN && user.getRole() != Role.SECONDARY_ADMIN
                && userRepository.countByRole(Role.SECONDARY_ADMIN) >= 3) {
            throw ApiException.conflict("二级管理员最多只能有3人");
        }

        String name = request.inspectorName() == null
                ? user.getInspectorName() : UserValues.name(request.inspectorName());
        String area = request.area() == null ? user.getArea() : UserValues.area(request.area());
        if (userRepository.existsByInspectorNameAndAreaAndIdNot(name, area, id)) {
            throw ApiException.conflict("该姓名和区域已被其他用户使用");
        }

        user.setInspectorName(name);
        user.setArea(area);
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.feishuOpenId() != null) {
            user.setFeishuOpenId(UserValues.feishuOpenId(request.feishuOpenId()));
        }

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("用户角色或姓名区域约束冲突");
        }
        if (!user.isEnabled()) {
            tokenRepository.deleteByUserId(user.getId());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateOwnFeishuOpenId(Long userId, String openId) {
        InspectorUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setFeishuOpenId(UserValues.feishuOpenId(openId));
        return UserResponse.from(userRepository.save(user));
    }
}
