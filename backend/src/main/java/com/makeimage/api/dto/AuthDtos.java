package com.makeimage.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 40) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 80) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank String account,
            @NotBlank String password
    ) {
    }

    public record UserView(Long id, String username, String email, String avatarUrl) {
    }

    public record AuthResponse(String token, UserView user) {
    }
}
