package com.tianji.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.user.dto.LoginResponse;
import com.tianji.user.dto.RegisterRequest;
import com.tianji.user.entity.User;
import com.tianji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== login ====================

    @Test
    void shouldLoginSuccessfully() {
        registerUser("testuser", "123456");

        LoginResponse response = userService.login("testuser", "123456");

        assertThat(response.getUserId()).isNotNull();
        assertThat(response.getUsername()).isEqualTo("testuser");
        assertThat(response.getToken()).isNotBlank();
    }

    @Test
    void shouldLoginFailWithWrongPassword() {
        registerUser("testuser", "123456");

        assertThatThrownBy(() -> userService.login("testuser", "wrong_password"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void shouldLoginFailWhenUserNotFound() {
        assertThatThrownBy(() -> userService.login("nobody", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void shouldLoginFailWhenAccountDisabled() {
        registerUser("testuser", "123456");
        // 手动禁用账号
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "testuser"));
        user.setStatus(0);
        userMapper.updateById(user);

        assertThatThrownBy(() -> userService.login("testuser", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已被禁用");
    }

    // ==================== register ====================

    @Test
    void shouldRegisterAndGetUserById() {
        RegisterRequest req = buildRegisterRequest("newuser", "mypassword");
        userService.register(req);

        // 通过查询验证注册结果
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "newuser"));
        assertThat(user).isNotNull();
        assertThat(user.getStatus()).isEqualTo(1);

        // 验证 getUserById 脱敏
        User result = userService.getUserById(user.getId());
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getPassword()).isNull();
    }

    @Test
    void shouldRejectDuplicateUsername() {
        RegisterRequest req = buildRegisterRequest("duplicate", "123456");
        userService.register(req);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");
    }

    // ==================== helpers ====================

    private void registerUser(String username, String password) {
        RegisterRequest req = buildRegisterRequest(username, password);
        userService.register(req);
    }

    private RegisterRequest buildRegisterRequest(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setPhone("13800000000");
        req.setEmail("test@tianji.com");
        return req;
    }
}
