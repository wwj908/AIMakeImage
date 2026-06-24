package com.makeimage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.Map;

public class AdminDtos {
    public record StatsView(
            long userCount,
            long artworkCount,
            long publicArtworkCount,
            long likeCount,
            long favoriteCount,
            long commentCount,
            long downloadCount
    ) {
    }

    public record AdminUserView(
            Long id,
            String username,
            String email,
            String avatarUrl,
            String role,
            LocalDateTime createdAt
    ) {
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "USER|ADMIN") String role) {
    }

    public record SettingsRequest(Map<String, String> settings) {
    }
}
