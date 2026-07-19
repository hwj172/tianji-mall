package com.tianji.user.controller;

import com.tianji.common.result.R;
import com.tianji.user.dto.*;
import com.tianji.user.entity.User;
import com.tianji.user.service.UserService;
import com.tianji.common.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public R<Void> updateAvatar(@RequestHeader("Authorization") String authHeader,
                                 @Valid @RequestBody UpdateAvatarRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        userService.updateAvatar(userId, req.getAvatar());
        return R.ok();
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@RequestHeader("Authorization") String authHeader,
                                   @Valid @RequestBody UpdatePasswordRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        userService.updatePassword(userId, req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }
}
