package com.tianji.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.user.dto.LoginResponse;
import com.tianji.user.dto.RegisterRequest;
import com.tianji.user.entity.User;
import com.tianji.user.mapper.UserMapper;
import com.tianji.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileStorageService fileStorageService;

    @Transactional
    public void register(RegisterRequest req) {
        // 检查用户名唯一
        long count = count(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count > 0) {
            throw new BizException(BizErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(1);

        try {
            save(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(BizErrorCode.USERNAME_EXISTS);
        }
    }

    public LoginResponse login(String username, String password) {
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BizException(BizErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() == 0) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(BizErrorCode.PASSWORD_ERROR);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(user.getId(), user.getUsername(), token, user.getRole());
    }

    public void updateProfile(Long userId, String username, String phone, String email) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(email);
        updateById(user);
    }

    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        String url = fileStorageService.saveFile(file);
        user.setAvatar(url);
        updateById(user);
        return url;
    }

    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(BizErrorCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        updateById(user);
    }

    public User getUserById(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        user.setPassword(null); // 不暴露密码
        return user;
    }

    public long countUsers() {
        return count();
    }

    public void promoteToSeller(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if ("seller".equals(user.getRole())) {
            throw new BizException(BizErrorCode.ALREADY_SELLER);
        }
        user.setRole("seller");
        updateById(user);
    }

    // ===== Admin 用户管理 =====

    private static final Set<String> VALID_ROLES = Set.of("user", "seller", "admin");

    public Page<User> listUsers(int page, int size, String keyword, String role, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> userPage = page(new Page<>(page, size), wrapper);
        // 脱敏：清除密码
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return userPage;
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(BizErrorCode.INVALID_STATUS);
        }
        User user = getById(id);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        updateById(user);
    }

    public void updateRole(Long id, String role) {
        if (role == null || !VALID_ROLES.contains(role)) {
            throw new BizException(BizErrorCode.INVALID_ROLE);
        }
        User user = getById(id);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        user.setRole(role);
        updateById(user);
    }
}
