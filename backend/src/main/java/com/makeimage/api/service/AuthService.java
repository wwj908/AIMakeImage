package com.makeimage.api.service;

import com.makeimage.api.dto.AuthDtos;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.JwtService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration EMAIL_CODE_TTL = Duration.ofMinutes(10);
    private static final Duration EMAIL_CODE_INTERVAL = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final SystemSettingService systemSettingService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            SystemSettingService systemSettingService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.systemSettingService = systemSettingService;
    }

    public void sendRegisterCode(AuthDtos.EmailCodeRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册");
        }
        String from = systemSettingService.value("mail.username");
        if (from == null || from.isBlank() || systemSettingService.value("mail.password").isBlank()) {
            throw new IllegalStateException("QQ 邮箱服务未配置");
        }
        String cooldownKey = emailCooldownKey(email);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new IllegalArgumentException("验证码发送太频繁，请稍后再试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(emailCodeKey(email), code, EMAIL_CODE_TTL);
        redisTemplate.opsForValue().set(cooldownKey, "1", EMAIL_CODE_INTERVAL);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("MakeImage 注册验证码");
        message.setText("你的 MakeImage 注册验证码是：" + code + "\n\n验证码 10 分钟内有效，请勿泄露给他人。");
        mailSender().send(message);
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        verifyEmailCode(email, request.emailCode());
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole("USER");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        redisTemplate.delete(emailCodeKey(email));
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

    private void verifyEmailCode(String email, String code) {
        String saved = redisTemplate.opsForValue().get(emailCodeKey(email));
        if (saved == null || !saved.equals(code == null ? "" : code.trim())) {
            throw new IllegalArgumentException("邮箱验证码错误或已过期");
        }
    }

    private String emailCodeKey(String email) {
        return "auth:email-code:" + email;
    }

    private String emailCooldownKey(String email) {
        return "auth:email-code-cooldown:" + email;
    }

    private AuthDtos.AuthResponse authResponse(User user) {
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthDtos.AuthResponse(token, new AuthDtos.UserView(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl(), user.getRole()));
    }

    private JavaMailSenderImpl mailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.qq.com");
        sender.setPort(465);
        sender.setProtocol("smtps");
        sender.setUsername(systemSettingService.value("mail.username"));
        sender.setPassword(systemSettingService.value("mail.password"));
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.ssl.enable", "true");
        return sender;
    }
}
