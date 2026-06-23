package com.makeimage.api.repository;

import com.makeimage.api.entity.ArtworkComment;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtworkCommentRepository extends JpaRepository<ArtworkComment, Long> {
    long countByArtworkId(Long artworkId);

    @EntityGraph(attributePaths = "user")
    List<ArtworkComment> findByArtworkId(Long artworkId, Sort sort);
}
