package com.example.paintquality.user;

import com.example.paintquality.auth.AuthTokenRepository;
import com.example.paintquality.auth.PasswordHasher;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.excel.AreaCatalog;
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
        return userRepository.findAllByOrderByIdAsc().stream()
                .filter(user -> !user.isDeleted())
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse update(InspectorUser actor, Long id, UpdateUserRequest request) {
        InspectorUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (user.isDeleted()) {
            throw ApiException.notFound("用户不存在");
        }
        if (actor.getRole() != Role.PRIMARY_ADMIN
                && request.role() != null && request.role() != user.getRole()) {
            throw ApiException.forbidden("只有一级管理员可以设置角色等级");
        }

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
        boolean duplicated = userRepository.findAllByInspectorName(name).stream()
                .anyMatch(other -> !other.getId().equals(id) && !other.isDeleted());
        if (duplicated) {
            throw ApiException.conflict("该检查人姓名已被其他用户使用");
        }

        user.setInspectorName(name);
        user.setArea(area);
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.checkArea() != null) {
            user.setCheckArea(UserValues.checkArea(request.checkArea()));
        }
        if (request.email() != null) {
            user.setEmail(UserValues.email(request.email()));
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
    public void delete(InspectorUser actor, Long id) {
        InspectorUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (user.isDeleted()) {
            return;
        }
        if (user.getRole() == Role.PRIMARY_ADMIN) {
            throw ApiException.conflict("一级管理员不可删除");
        }
        if (user.getId().equals(actor.getId())) {
            throw ApiException.conflict("不能删除自己的账号");
        }
        tokenRepository.deleteByUserId(user.getId());
        String suffix = "#" + user.getId();
        String base = user.getInspectorName();
        if (base.length() > 80 - suffix.length()) {
            base = base.substring(0, 80 - suffix.length());
        }
        user.setInspectorName(base + suffix);
        user.setDeleted(true);
        user.setEnabled(false);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public UserResponse updateOwnEmail(Long userId, String email) {
        InspectorUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setEmail(UserValues.email(email));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void updateOwnPassword(Long userId, String newPassword) {
        InspectorUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setPasswordHash(PasswordHasher.hash(UserValues.password(newPassword)));
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public UserResponse updateOwnAreas(Long userId, String area, String checkArea) {
        String responsibility = AreaCatalog.requireResponsibility(area);
        String target = UserValues.checkArea(checkArea);
        if (target == null || !AreaCatalog.isAllowed(responsibility, target)) {
            throw ApiException.badRequest("检查区域必须属于所选责任区域");
        }
        InspectorUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setArea(responsibility);
        user.setCheckArea(target);
        return UserResponse.from(userRepository.save(user));
    }
}
