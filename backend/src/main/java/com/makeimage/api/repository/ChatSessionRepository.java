package com.makeimage.api.repository;

import com.makeimage.api.entity.ChatSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    @EntityGraph(attributePaths = "owner")
    List<ChatSession> findByOwnerIdOrderByPinnedDescUpdatedAtDesc(Long ownerId);

    Optional<ChatSession> findByOwnerIdAndClientId(Long ownerId, String clientId);
}
