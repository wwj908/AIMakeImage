package com.makeimage.api.controller;

import com.makeimage.api.dto.ApiResponse;
import com.makeimage.api.dto.ChatSessionDtos;
import com.makeimage.api.service.ChatSessionService;
import com.makeimage.api.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-sessions")
public class ChatSessionController {
    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    public ApiResponse<List<ChatSessionDtos.ChatSessionView>> list() {
        return ApiResponse.ok(chatSessionService.list(SecurityUtils.currentUser()));
    }

    @PostMapping
    public ApiResponse<ChatSessionDtos.ChatSessionView> save(@Valid @RequestBody ChatSessionDtos.SaveRequest request) {
        return ApiResponse.ok(chatSessionService.save(SecurityUtils.currentUser(), request));
    }

    @PatchMapping("/{clientId}/rename")
    public ApiResponse<ChatSessionDtos.ChatSessionView> rename(
            @PathVariable String clientId,
            @Valid @RequestBody ChatSessionDtos.RenameRequest request
    ) {
        return ApiResponse.ok(chatSessionService.rename(SecurityUtils.currentUser(), clientId, request));
    }

    @PatchMapping("/{clientId}/pin")
    public ApiResponse<ChatSessionDtos.ChatSessionView> pin(
            @PathVariable String clientId,
            @RequestBody ChatSessionDtos.PinRequest request
    ) {
        return ApiResponse.ok(chatSessionService.pin(SecurityUtils.currentUser(), clientId, request));
    }

    @DeleteMapping("/{clientId}")
    public ApiResponse<Void> delete(@PathVariable String clientId) {
        chatSessionService.delete(SecurityUtils.currentUser(), clientId);
        return ApiResponse.ok(null);
    }
}
