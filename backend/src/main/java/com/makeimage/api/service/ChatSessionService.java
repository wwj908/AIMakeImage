package com.makeimage.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeimage.api.dto.ChatSessionDtos;
import com.makeimage.api.entity.ChatSession;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.ChatSessionRepository;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatSessionService {
    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatSessionService(
            ChatSessionRepository chatSessionRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<ChatSessionDtos.ChatSessionView> list(CurrentUser currentUser) {
        return chatSessionRepository.findByOwnerIdOrderByPinnedDescUpdatedAtDesc(currentUser.id())
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ChatSessionDtos.ChatSessionView save(CurrentUser currentUser, ChatSessionDtos.SaveRequest request) {
        User owner = userRepository.getReferenceById(currentUser.id());
        ChatSession session = chatSessionRepository.findByOwnerIdAndClientId(currentUser.id(), request.clientId())
                .orElseGet(ChatSession::new);
        session.setOwner(owner);
        session.setClientId(request.clientId());
        session.setTitle(request.title().trim());
        session.setPinned(Boolean.TRUE.equals(request.pinned()));
        session.setMessagesJson(normalizeMessagesJson(request.messagesJson()));
        return toView(chatSessionRepository.saveAndFlush(session));
    }

    @Transactional
    public ChatSessionDtos.ChatSessionView rename(CurrentUser currentUser, String clientId, ChatSessionDtos.RenameRequest request) {
        ChatSession session = ownedSession(currentUser, clientId);
        session.setTitle(request.title().trim());
        return toView(chatSessionRepository.saveAndFlush(session));
    }

    @Transactional
    public ChatSessionDtos.ChatSessionView pin(CurrentUser currentUser, String clientId, ChatSessionDtos.PinRequest request) {
        ChatSession session = ownedSession(currentUser, clientId);
        session.setPinned(Boolean.TRUE.equals(request.pinned()));
        return toView(chatSessionRepository.saveAndFlush(session));
    }

    @Transactional
    public void delete(CurrentUser currentUser, String clientId) {
        ChatSession session = ownedSession(currentUser, clientId);
        chatSessionRepository.delete(session);
    }

    private ChatSession ownedSession(CurrentUser currentUser, String clientId) {
        return chatSessionRepository.findByOwnerIdAndClientId(currentUser.id(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
    }

    private String normalizeMessagesJson(String messagesJson) {
        if (messagesJson == null || messagesJson.isBlank()) {
            return "[]";
        }
        try {
            JsonNode node = objectMapper.readTree(messagesJson);
            if (!node.isArray()) {
                throw new IllegalArgumentException("会话内容格式不正确");
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalArgumentException("会话内容格式不正确");
        }
    }

    private ChatSessionDtos.ChatSessionView toView(ChatSession session) {
        return new ChatSessionDtos.ChatSessionView(
                session.getId(),
                session.getClientId(),
                session.getTitle(),
                session.getPinned(),
                session.getMessagesJson(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
