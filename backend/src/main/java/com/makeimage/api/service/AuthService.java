package com.makeimage.api.service;

import com.makeimage.api.dto.AuthDtos;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("邮箱已注册");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return authResponse(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByUsername(request.account())
                .or(() -> userRepository.findByEmail(request.account()))
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return authResponse(user);
    }

    private AuthDtos.AuthResponse authResponse(User user) {
        String token = jwtService.createToken(user.getId(), user.getUsername());
        return new AuthDtos.AuthResponse(token, new AuthDtos.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl()));
    }
}
