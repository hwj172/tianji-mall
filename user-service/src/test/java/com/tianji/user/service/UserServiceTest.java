package com.tianji.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.common.util.JwtUtil;
import com.tianji.user.dto.RegisterRequest;
import com.tianji.user.entity.User;
import com.tianji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(passwordEncoder, jwtUtil);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    // ==================== getUserById ====================

    @Test
    void shouldGetUserById() {
        User user = buildUser(1L, "testuser", "hashed_pw");
        when(userMapper.selectById(1L)).thenReturn(user);

        User result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isNull(); // 密码应被清除
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    // ==================== register ====================

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = buildRegisterRequest("newuser", "123456", "13800001111", "new@test.com");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("hashed_new_pw");

        userService.register(req);

        // 验证 save 被调用（insert 被触发）
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void shouldThrowWhenUsernameExists() {
        RegisterRequest req = buildRegisterRequest("existing", "123456", "13800001111", "e@test.com");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void shouldEncodePasswordOnRegister() {
        RegisterRequest req = buildRegisterRequest("newuser", "plain_pw", "13800001111", "new@test.com");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("plain_pw")).thenReturn("encoded_pw");

        userService.register(req);

        verify(passwordEncoder).encode("plain_pw");
    }

    // ==================== helpers ====================

    private User buildUser(Long id, String username, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setPhone("13800000000");
        user.setEmail("test@tianji.com");
        user.setStatus(1);
        return user;
    }

    private RegisterRequest buildRegisterRequest(String username, String password, String phone, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setPhone(phone);
        req.setEmail(email);
        return req;
    }
}
