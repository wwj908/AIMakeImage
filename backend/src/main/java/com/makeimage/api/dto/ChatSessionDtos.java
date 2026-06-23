package com.makeimage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ChatSessionDtos {
    public record SaveRequest(
            @NotBlank @Size(max = 80) String clientId,
            @NotBlank @Size(max = 120) String title,
            Boolean pinned,
            String messagesJson
    ) {
    }

    public record RenameRequest(@NotBlank @Size(max = 120) String title) {
    }

    public record PinRequest(Boolean pinned) {
    }

    public record ChatSessionView(
            Long id,
            String clientId,
            String title,
            Boolean pinned,
            String messagesJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
