package com.makeimage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ArtworkDtos {
    public record GenerateRequest(
            @NotBlank @Size(max = 1200) String prompt,
            @Size(max = 1200) String negativePrompt,
            @Size(max = 120) String title,
            Boolean publicWork
    ) {
    }

    public record EditRequest(
            @NotBlank @Size(max = 1200) String prompt,
            @Size(max = 1200) String negativePrompt,
            @Size(max = 120) String title,
            Boolean publicWork
    ) {
    }

    public record UploadRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 1200) String prompt,
            @Size(max = 300) String tags,
            @Size(max = 24) String ratio,
            Boolean publicWork
    ) {
    }

    public record PublishRequest(Boolean publicWork) {
    }

    public record MetadataRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 300) String tags
    ) {
    }

    public record CommentRequest(@NotBlank @Size(max = 500) String content) {
    }

    public record CommentView(
            Long id,
            Long userId,
            String username,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record ArtworkView(
            Long id,
            Long ownerId,
            String ownerName,
            String title,
            String tags,
            String prompt,
            String negativePrompt,
            String mode,
            String imageUrl,
            String sourceImageUrl,
            Boolean publicWork,
            Long downloadCount,
            Long likeCount,
            Long favoriteCount,
            Long commentCount,
            Boolean liked,
            Boolean favorited,
            LocalDateTime createdAt
    ) {
    }
}
