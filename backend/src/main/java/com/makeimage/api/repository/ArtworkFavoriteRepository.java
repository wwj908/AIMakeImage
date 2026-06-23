package com.makeimage.api.repository;

import com.makeimage.api.entity.ArtworkFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtworkFavoriteRepository extends JpaRepository<ArtworkFavorite, Long> {
    long countByArtworkId(Long artworkId);

    boolean existsByArtworkIdAndUserId(Long artworkId, Long userId);

    Optional<ArtworkFavorite> findByArtworkIdAndUserId(Long artworkId, Long userId);
}
