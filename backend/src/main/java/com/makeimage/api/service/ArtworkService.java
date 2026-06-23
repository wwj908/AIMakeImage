package com.makeimage.api.service;

import com.makeimage.api.dto.ArtworkDtos;
import com.makeimage.api.entity.Artwork;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.ArtworkRepository;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class ArtworkService {
    private final ArtworkRepository artworkRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ImageRenderService imageRenderService;
    private final StringRedisTemplate redisTemplate;

    public ArtworkService(
            ArtworkRepository artworkRepository,
            UserRepository userRepository,
            StorageService storageService,
            ImageRenderService imageRenderService,
            StringRedisTemplate redisTemplate
    ) {
        this.artworkRepository = artworkRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.imageRenderService = imageRenderService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public ArtworkDtos.ArtworkView generate(CurrentUser currentUser, ArtworkDtos.GenerateRequest request) throws IOException {
        rateLimit(currentUser.id());
        User owner = userRepository.findById(currentUser.id()).orElseThrow();
        String filename = UUID.randomUUID() + ".png";
        imageRenderService.render(request.prompt(), request.negativePrompt(), "generate", storageService.generatedPath(filename), null);

        Artwork artwork = new Artwork();
        artwork.setOwner(owner);
        artwork.setTitle(resolveTitle(request.title(), request.prompt()));
        artwork.setPrompt(request.prompt());
        artwork.setNegativePrompt(request.negativePrompt());
        artwork.setMode("generate");
        artwork.setImageUrl(storageService.publicGeneratedUrl(filename));
        artwork.setPublicWork(Boolean.TRUE.equals(request.publicWork()));
        return toView(artworkRepository.save(artwork));
    }

    @Transactional
    public ArtworkDtos.ArtworkView edit(CurrentUser currentUser, MultipartFile sourceImage, ArtworkDtos.EditRequest request) throws IOException {
        rateLimit(currentUser.id());
        User owner = userRepository.findById(currentUser.id()).orElseThrow();
        String sourceUrl = storageService.saveUpload(sourceImage);
        String filename = UUID.randomUUID() + ".png";
        imageRenderService.render(request.prompt(), request.negativePrompt(), "edit", storageService.generatedPath(filename), sourceImage);

        Artwork artwork = new Artwork();
        artwork.setOwner(owner);
        artwork.setTitle(resolveTitle(request.title(), request.prompt()));
        artwork.setPrompt(request.prompt());
        artwork.setNegativePrompt(request.negativePrompt());
        artwork.setMode("edit");
        artwork.setSourceImageUrl(sourceUrl);
        artwork.setImageUrl(storageService.publicGeneratedUrl(filename));
        artwork.setPublicWork(Boolean.TRUE.equals(request.publicWork()));
        return toView(artworkRepository.save(artwork));
    }

    public Page<ArtworkDtos.ArtworkView> publicWorks(Pageable pageable) {
        return artworkRepository.findByPublicWorkTrue(pageable).map(this::toView);
    }

    public Page<ArtworkDtos.ArtworkView> myWorks(CurrentUser currentUser, Pageable pageable) {
        return artworkRepository.findByOwnerId(currentUser.id(), pageable).map(this::toView);
    }

    @Transactional
    public ArtworkDtos.ArtworkView setPublic(CurrentUser currentUser, Long id, boolean publicWork) {
        Artwork artwork = ownedArtwork(currentUser, id);
        artwork.setPublicWork(publicWork);
        return toView(artworkRepository.saveAndFlush(artwork));
    }

    @Transactional
    public ArtworkDtos.ArtworkView addDownload(Long id) {
        Artwork artwork = artworkRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("作品不存在"));
        if (!artwork.getPublicWork()) {
            throw new IllegalArgumentException("作品未公开");
        }
        artwork.setDownloadCount(artwork.getDownloadCount() + 1);
        return toView(artwork);
    }

    private Artwork ownedArtwork(CurrentUser currentUser, Long id) {
        Artwork artwork = artworkRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("作品不存在"));
        if (!artwork.getOwner().getId().equals(currentUser.id())) {
            throw new IllegalArgumentException("无权操作该作品");
        }
        return artwork;
    }

    private void rateLimit(Long userId) {
        try {
            String key = "image:generate:user:" + userId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            if (count != null && count > 20) {
                throw new IllegalArgumentException("生成太频繁，请稍后再试");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception ignored) {
            // Redis is used for soft rate limiting. Image generation should still work in local dev if Redis is offline.
        }
    }

    private String resolveTitle(String title, String prompt) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return prompt.length() > 24 ? prompt.substring(0, 24) : prompt;
    }

    private ArtworkDtos.ArtworkView toView(Artwork artwork) {
        return new ArtworkDtos.ArtworkView(
                artwork.getId(),
                artwork.getOwner().getId(),
                artwork.getOwner().getUsername(),
                artwork.getTitle(),
                artwork.getPrompt(),
                artwork.getNegativePrompt(),
                artwork.getMode(),
                artwork.getImageUrl(),
                artwork.getSourceImageUrl(),
                artwork.getPublicWork(),
                artwork.getDownloadCount(),
                artwork.getCreatedAt()
        );
    }
}
