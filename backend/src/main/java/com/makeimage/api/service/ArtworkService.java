package com.makeimage.api.service;

import com.makeimage.api.dto.ArtworkDtos;
import com.makeimage.api.entity.Artwork;
import com.makeimage.api.entity.ArtworkComment;
import com.makeimage.api.entity.ArtworkFavorite;
import com.makeimage.api.entity.ArtworkLike;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.ArtworkCommentRepository;
import com.makeimage.api.repository.ArtworkFavoriteRepository;
import com.makeimage.api.repository.ArtworkLikeRepository;
import com.makeimage.api.repository.ArtworkRepository;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ArtworkService {
    private final ArtworkRepository artworkRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final ImageRenderService imageRenderService;
    private final StringRedisTemplate redisTemplate;
    private final ArtworkLikeRepository artworkLikeRepository;
    private final ArtworkFavoriteRepository artworkFavoriteRepository;
    private final ArtworkCommentRepository artworkCommentRepository;
    private final ThumbnailService thumbnailService;

    public ArtworkService(
            ArtworkRepository artworkRepository,
            UserRepository userRepository,
            StorageService storageService,
            ImageRenderService imageRenderService,
            StringRedisTemplate redisTemplate,
            ArtworkLikeRepository artworkLikeRepository,
            ArtworkFavoriteRepository artworkFavoriteRepository,
            ArtworkCommentRepository artworkCommentRepository,
            ThumbnailService thumbnailService
    ) {
        this.artworkRepository = artworkRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.imageRenderService = imageRenderService;
        this.redisTemplate = redisTemplate;
        this.artworkLikeRepository = artworkLikeRepository;
        this.artworkFavoriteRepository = artworkFavoriteRepository;
        this.artworkCommentRepository = artworkCommentRepository;
        this.thumbnailService = thumbnailService;
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
        return toView(artworkRepository.save(artwork), currentUser);
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
        return toView(artworkRepository.save(artwork), currentUser);
    }

    @Transactional
    public ArtworkDtos.ArtworkView upload(CurrentUser currentUser, MultipartFile image, ArtworkDtos.UploadRequest request) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请上传作品图片");
        }
        User owner = userRepository.findById(currentUser.id()).orElseThrow();
        String imageUrl = storageService.saveUpload(image);

        Artwork artwork = new Artwork();
        artwork.setOwner(owner);
        artwork.setTitle(request.title().trim());
        artwork.setTags(normalizeTags(request.tags()));
        artwork.setPrompt(request.prompt());
        artwork.setMode("upload");
        artwork.setImageUrl(imageUrl);
        artwork.setPublicWork(Boolean.TRUE.equals(request.publicWork()));
        return toView(artworkRepository.save(artwork), currentUser);
    }

    public Page<ArtworkDtos.ArtworkView> publicWorks(CurrentUser currentUser, Pageable pageable) {
        return artworkRepository.findByPublicWorkTrue(pageable).map(artwork -> toView(artwork, currentUser));
    }

    public Page<ArtworkDtos.ArtworkView> myWorks(CurrentUser currentUser, Pageable pageable) {
        return artworkRepository.findByOwnerId(currentUser.id(), pageable).map(artwork -> toView(artwork, currentUser));
    }

    @Transactional
    public ArtworkDtos.ArtworkView setPublic(CurrentUser currentUser, Long id, boolean publicWork) {
        Artwork artwork = ownedArtwork(currentUser, id);
        artwork.setPublicWork(publicWork);
        return toView(artworkRepository.saveAndFlush(artwork), currentUser);
    }

    @Transactional
    public ArtworkDtos.ArtworkView updateMetadata(CurrentUser currentUser, Long id, ArtworkDtos.MetadataRequest request) {
        Artwork artwork = ownedArtwork(currentUser, id);
        artwork.setTitle(request.title().trim());
        artwork.setTags(normalizeTags(request.tags()));
        return toView(artworkRepository.saveAndFlush(artwork), currentUser);
    }

    @Transactional
    public ArtworkDtos.ArtworkView addDownload(Long id) {
        Artwork artwork = publicArtwork(id);
        artwork.setDownloadCount(artwork.getDownloadCount() + 1);
        return toView(artwork, null);
    }

    @Transactional
    public ArtworkDtos.ArtworkView toggleLike(CurrentUser currentUser, Long id) {
        Artwork artwork = publicArtwork(id);
        artworkLikeRepository.findByArtworkIdAndUserId(id, currentUser.id())
                .ifPresentOrElse(
                        artworkLikeRepository::delete,
                        () -> {
                            ArtworkLike like = new ArtworkLike();
                            like.setArtwork(artwork);
                            like.setUser(userRepository.getReferenceById(currentUser.id()));
                            artworkLikeRepository.save(like);
                        }
                );
        return toView(artwork, currentUser);
    }

    @Transactional
    public ArtworkDtos.ArtworkView toggleFavorite(CurrentUser currentUser, Long id) {
        Artwork artwork = publicArtwork(id);
        artworkFavoriteRepository.findByArtworkIdAndUserId(id, currentUser.id())
                .ifPresentOrElse(
                        artworkFavoriteRepository::delete,
                        () -> {
                            ArtworkFavorite favorite = new ArtworkFavorite();
                            favorite.setArtwork(artwork);
                            favorite.setUser(userRepository.getReferenceById(currentUser.id()));
                            artworkFavoriteRepository.save(favorite);
                        }
                );
        return toView(artwork, currentUser);
    }

    @Transactional
    public ArtworkDtos.CommentView addComment(CurrentUser currentUser, Long id, ArtworkDtos.CommentRequest request) {
        Artwork artwork = publicArtwork(id);
        String content = request.content().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("评论不能为空");
        }
        ArtworkComment comment = new ArtworkComment();
        comment.setArtwork(artwork);
        comment.setUser(userRepository.getReferenceById(currentUser.id()));
        comment.setContent(content);
        return toCommentView(artworkCommentRepository.save(comment));
    }

    public List<ArtworkDtos.CommentView> comments(Long id) {
        publicArtwork(id);
        return artworkCommentRepository.findByArtworkId(id, Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(this::toCommentView)
                .toList();
    }

    private Artwork ownedArtwork(CurrentUser currentUser, Long id) {
        Artwork artwork = artworkRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("作品不存在"));
        if (!artwork.getOwner().getId().equals(currentUser.id())) {
            throw new IllegalArgumentException("无权操作该作品");
        }
        return artwork;
    }

    private Artwork publicArtwork(Long id) {
        Artwork artwork = artworkRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("作品不存在"));
        if (!Boolean.TRUE.equals(artwork.getPublicWork())) {
            throw new IllegalArgumentException("作品未公开");
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

    private ArtworkDtos.ArtworkView toView(Artwork artwork, CurrentUser currentUser) {
        Long artworkId = artwork.getId();
        Long currentUserId = currentUser == null ? null : currentUser.id();
        return new ArtworkDtos.ArtworkView(
                artwork.getId(),
                artwork.getOwner().getId(),
                artwork.getOwner().getUsername(),
                artwork.getTitle(),
                artwork.getTags(),
                artwork.getPrompt(),
                artwork.getNegativePrompt(),
                artwork.getMode(),
                artwork.getImageUrl(),
                thumbnailUrl(artwork),
                artwork.getSourceImageUrl(),
                artwork.getPublicWork(),
                artwork.getDownloadCount(),
                artworkLikeRepository.countByArtworkId(artworkId),
                artworkFavoriteRepository.countByArtworkId(artworkId),
                artworkCommentRepository.countByArtworkId(artworkId),
                currentUserId != null && artworkLikeRepository.existsByArtworkIdAndUserId(artworkId, currentUserId),
                currentUserId != null && artworkFavoriteRepository.existsByArtworkIdAndUserId(artworkId, currentUserId),
                artwork.getCreatedAt()
        );
    }

    private String thumbnailUrl(Artwork artwork) {
        String imageUrl = artwork.getImageUrl();
        if (imageUrl == null) {
            return null;
        }
        if (!imageUrl.contains("/files/generated/")) {
            return imageUrl;
        }
        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        try {
            thumbnailService.ensureThumbnail(filename);
        } catch (IOException ignored) {
            return imageUrl;
        }
        return storageService.publicThumbnailUrl(filename);
    }

    private ArtworkDtos.CommentView toCommentView(ArtworkComment comment) {
        return new ArtworkDtos.CommentView(
                comment.getId(),
                comment.getUser().getId(),
                comment.getUser().getUsername(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        return tags.trim().replace('，', ',');
    }
}
