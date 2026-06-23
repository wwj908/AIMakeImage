package com.makeimage.api.repository;

import com.makeimage.api.entity.ArtworkLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtworkLikeRepository extends JpaRepository<ArtworkLike, Long> {
    long countByArtworkId(Long artworkId);

    boolean existsByArtworkIdAndUserId(Long artworkId, Long userId);

    Optional<ArtworkLike> findByArtworkIdAndUserId(Long artworkId, Long userId);
}
