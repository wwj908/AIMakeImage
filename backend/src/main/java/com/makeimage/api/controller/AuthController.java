package com.makeimage.api.controller;

import com.makeimage.api.dto.ApiResponse;
import com.makeimage.api.dto.AuthDtos;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.service.AuthService;
import com.makeimage.api.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/email-code")
    public ApiResponse<Void> emailCode(@Valid @RequestBody AuthDtos.EmailCodeRequest request) {
        authService.sendRegisterCode(request);
        return ApiResponse.ok("验证码已发送", null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserView> me() {
        var current = SecurityUtils.currentUser();
        var user = userRepository.findById(current.id()).orElseThrow();
        return ApiResponse.ok(new AuthDtos.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl()));
    }
}
