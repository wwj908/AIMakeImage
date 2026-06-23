package com.makeimage.api.repository;

import com.makeimage.api.entity.Artwork;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkRepository extends JpaRepository<Artwork, Long> {
    @EntityGraph(attributePaths = "owner")
    Page<Artwork> findByPublicWorkTrue(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Artwork> findByOwnerId(Long ownerId, Pageable pageable);
}
