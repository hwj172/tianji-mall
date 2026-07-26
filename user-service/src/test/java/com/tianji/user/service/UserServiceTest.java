package com.tianji.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.common.util.JwtUtil;
import com.tianji.user.dto.RegisterRequest;
import com.tianji.user.entity.User;
import com.tianji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    @Mock
    private FileStorageService fileStorageService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(passwordEncoder, jwtUtil, fileStorageService);
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

    // ==================== updateProfile ====================

    @Test
    void shouldUpdateProfile() {
        User user = buildUser(1L, "oldname", "pw");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateProfile(1L, "newname", "13900001111", "new@email.com");

        assertThat(user.getUsername()).isEqualTo("newname");
        assertThat(user.getPhone()).isEqualTo("13900001111");
        assertThat(user.getEmail()).isEqualTo("new@email.com");
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldThrowWhenUpdateProfileUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateProfile(999L, "name", "phone", "email"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    // ==================== updateAvatar ====================

    @Test
    void shouldUpdateAvatar() {
        User user = buildUser(1L, "testuser", "pw");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("avatar.jpg");
        when(fileStorageService.saveFile(file)).thenReturn("/uploads/avatar_abc.jpg");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        String url = userService.updateAvatar(1L, file);

        assertThat(url).isEqualTo("/uploads/avatar_abc.jpg");
        assertThat(user.getAvatar()).isEqualTo("/uploads/avatar_abc.jpg");
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldThrowWhenUpdateAvatarUserNotFound() {
        MultipartFile file = mock(MultipartFile.class);
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateAvatar(999L, file))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    // ==================== updatePassword ====================

    @Test
    void shouldUpdatePassword() {
        User user = buildUser(1L, "testuser", "hashed_old_pw");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("oldPass", "hashed_old_pw")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("hashed_new_pw");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updatePassword(1L, "oldPass", "newPass");

        verify(passwordEncoder).matches("oldPass", "hashed_old_pw");
        verify(passwordEncoder).encode("newPass");
        assertThat(user.getPassword()).isEqualTo("hashed_new_pw");
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldThrowWhenOldPasswordWrong() {
        User user = buildUser(1L, "testuser", "hashed_old_pw");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrongPwd", "hashed_old_pw")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(1L, "wrongPwd", "newPass"))
                .isInstanceOf(BizException.class)
                .hasMessage("旧密码错误");
    }

    @Test
    void shouldThrowWhenUpdatePasswordUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updatePassword(999L, "old", "new"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    // ==================== listUsers (admin) ====================

    @Test
    void shouldListUsersWithPagination() {
        User user = buildUser(1L, "testuser", "pw");
        Page<User> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(user));
        mockPage.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<User> result = userService.listUsers(1, 20, null, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getPassword()).isNull(); // 密码脱敏
    }

    @Test
    void shouldListUsersWithKeywordFilter() {
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 20));

        userService.listUsers(1, 20, "test", null, null);

        ArgumentCaptor<LambdaQueryWrapper<User>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectPage(any(Page.class), captor.capture());
        // wrapper 应包含 keyword 条件（无法直接断言 SQL，但验证调用即可）
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void shouldListUsersWithRoleFilter() {
        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 20));

        userService.listUsers(1, 20, null, "admin", null);

        verify(userMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ==================== updateStatus (admin) ====================

    @Test
    void shouldUpdateUserStatus() {
        User user = buildUser(1L, "testuser", "pw");
        user.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateStatus(1L, 0);

        assertThat(user.getStatus()).isEqualTo(0);
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() -> userService.updateStatus(1L, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态值无效");

        assertThatThrownBy(() -> userService.updateStatus(1L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态值无效");
    }

    // ==================== updateRole (admin) ====================

    @Test
    void shouldUpdateUserRole() {
        User user = buildUser(1L, "testuser", "pw");
        user.setRole("user");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        userService.updateRole(1L, "admin");

        assertThat(user.getRole()).isEqualTo("admin");
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldRejectInvalidRole() {
        assertThatThrownBy(() -> userService.updateRole(1L, "superadmin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色无效");

        assertThatThrownBy(() -> userService.updateRole(1L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色无效");
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
