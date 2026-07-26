package com.tianji.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.dto.UserDTO;
import com.tianji.common.result.R;
import com.tianji.user.dto.*;
import com.tianji.user.entity.User;
import com.tianji.user.service.UserService;
import com.tianji.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return R.ok();
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse resp = userService.login(req.getUsername(), req.getPassword());
        return R.ok(resp);
    }

    @GetMapping("/info")
    public R<User> info(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        Long userId = jwtUtil.getUserId(token);
        User user = userService.getUserById(userId);
        return R.ok(user);
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestHeader("Authorization") String authHeader,
                                  @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        userService.updateProfile(userId, req.getUsername(), req.getPhone(), req.getEmail());
        return R.ok();
    }

    @PutMapping("/avatar")
    public R<String> updateAvatar(@RequestHeader("Authorization") String authHeader,
                                   @RequestParam("file") MultipartFile file) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        String url = userService.updateAvatar(userId, file);
        return R.ok(url);
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@RequestHeader("Authorization") String authHeader,
                                   @Valid @RequestBody UpdatePasswordRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        userService.updatePassword(userId, req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }

    // ===== 内部端点（网关 X-Internal-Token 鉴权，不暴露给前端）=====

    @GetMapping("/internal/count")
    public R<Long> countUsers() {
        return R.ok(userService.countUsers());
    }

    @PutMapping("/internal/promote")
    public R<Void> promoteToSeller(@RequestParam("userId") Long userId) {
        userService.promoteToSeller(userId);
        return R.ok();
    }

    @GetMapping("/internal/list")
    public R<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        Page<User> userPage = userService.listUsers(page, size, keyword, role, status);
        List<UserDTO> dtos = userPage.getRecords().stream().map(u -> {
            UserDTO dto = new UserDTO();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setPhone(u.getPhone());
            dto.setEmail(u.getEmail());
            dto.setAvatar(u.getAvatar());
            dto.setRole(u.getRole());
            dto.setStatus(u.getStatus());
            dto.setCreateTime(u.getCreateTime());
            return dto;
        }).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("records", dtos);
        result.put("total", userPage.getTotal());
        result.put("page", page);
        result.put("size", size);
        return R.ok(result);
    }

    @PutMapping("/internal/{id}/status")
    public R<Void> updateUserStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    @PutMapping("/internal/{id}/role")
    public R<Void> updateUserRole(@PathVariable("id") Long id,
                                   @RequestParam("role") String role) {
        userService.updateRole(id, role);
        return R.ok();
    }
}
