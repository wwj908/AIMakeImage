package com.makeimage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;
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

    public record OpenAiProviderView(
            Long id,
            String name,
            String baseUrl,
            String apiKey,
            String model,
            boolean enabled,
            Integer sortOrder
    ) {
    }

    public record OpenAiProviderRequest(
            Long id,
            String name,
            String baseUrl,
            String apiKey,
            String model,
            Boolean enabled,
            Integer sortOrder
    ) {
    }

    public record OpenAiProvidersRequest(List<OpenAiProviderRequest> providers) {
    }

    public record DeployRequest(
            @NotBlank String sourceUrl,
            @NotBlank String targetDir,
            String restartCommand
    ) {
    }

    public record DeployResultView(
            String sourceUrl,
            String targetDir,
            long downloadedBytes,
            int extractedFiles,
            boolean usedNestedRelease,
            boolean restarted,
            String restartMessage,
            LocalDateTime finishedAt
    ) {
    }

    public record DeployRuntimeView(
            String osName,
            String runtime,
            String recommendedCommand,
            String description
    ) {
    }

    public record DeployStepView(
            String key,
            String name,
            String status,
            String message,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }

    public record DeployJobView(
            String id,
            String status,
            String currentStep,
            int progress,
            String message,
            DeployRuntimeView runtime,
            List<DeployStepView> steps,
            DeployResultView result,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
