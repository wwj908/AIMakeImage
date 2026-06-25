package com.makeimage.api.service;

import com.makeimage.api.dto.AdminDtos;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.ArtworkCommentRepository;
import com.makeimage.api.repository.ArtworkFavoriteRepository;
import com.makeimage.api.repository.ArtworkLikeRepository;
import com.makeimage.api.repository.ArtworkRepository;
import com.makeimage.api.repository.UserRepository;
import com.makeimage.api.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final UserRepository userRepository;
    private final ArtworkRepository artworkRepository;
    private final ArtworkLikeRepository artworkLikeRepository;
    private final ArtworkFavoriteRepository artworkFavoriteRepository;
    private final ArtworkCommentRepository artworkCommentRepository;
    private final SystemSettingService systemSettingService;
    private final OpenAiProviderService openAiProviderService;
    private final StorageService storageService;
    private final ThumbnailService thumbnailService;
    private final DeployService deployService;

    public AdminService(
            UserRepository userRepository,
            ArtworkRepository artworkRepository,
            ArtworkLikeRepository artworkLikeRepository,
            ArtworkFavoriteRepository artworkFavoriteRepository,
            ArtworkCommentRepository artworkCommentRepository,
            SystemSettingService systemSettingService,
            OpenAiProviderService openAiProviderService,
            StorageService storageService,
            ThumbnailService thumbnailService,
            DeployService deployService
    ) {
        this.userRepository = userRepository;
        this.artworkRepository = artworkRepository;
        this.artworkLikeRepository = artworkLikeRepository;
        this.artworkFavoriteRepository = artworkFavoriteRepository;
        this.artworkCommentRepository = artworkCommentRepository;
        this.systemSettingService = systemSettingService;
        this.openAiProviderService = openAiProviderService;
        this.storageService = storageService;
        this.thumbnailService = thumbnailService;
        this.deployService = deployService;
    }

    public AdminDtos.StatsView stats(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return new AdminDtos.StatsView(
                userRepository.count(),
                artworkRepository.count(),
                artworkRepository.countByPublicWorkTrue(),
                artworkLikeRepository.count(),
                artworkFavoriteRepository.count(),
                artworkCommentRepository.count(),
                artworkRepository.sumDownloadCount()
        );
    }

    public List<AdminDtos.AdminUserView> users(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return userRepository.findAll().stream()
                .map(this::toUserView)
                .toList();
    }

    @Transactional
    public AdminDtos.AdminUserView updateRole(CurrentUser currentUser, Long userId, String role) {
        requireAdmin(currentUser);
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRole(role);
        return toUserView(userRepository.saveAndFlush(user));
    }

    public Map<String, String> settings(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return systemSettingService.allForAdmin();
    }

    public List<AdminDtos.OpenAiProviderView> openAiProviders(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return openAiProviderService.allForAdmin();
    }

    @Transactional
    public Map<String, String> updateSettings(CurrentUser currentUser, Map<String, String> settings) {
        requireAdmin(currentUser);
        return systemSettingService.update(settings == null ? Map.of() : settings);
    }

    @Transactional
    public List<AdminDtos.OpenAiProviderView> updateOpenAiProviders(CurrentUser currentUser, List<AdminDtos.OpenAiProviderRequest> providers) {
        requireAdmin(currentUser);
        return openAiProviderService.update(providers);
    }

    @Transactional
    public Map<String, String> uploadLogo(CurrentUser currentUser, MultipartFile file) throws Exception {
        requireAdmin(currentUser);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a logo image");
        }
        String logoUrl = storageService.saveSystemAsset(file);
        return systemSettingService.update(Map.of("system.logoUrl", logoUrl));
    }

    @Transactional
    public Integer regenerateThumbnails(CurrentUser currentUser) throws Exception {
        requireAdmin(currentUser);
        return thumbnailService.generateMissingThumbnails();
    }

    public AdminDtos.DeployJobView startDeployFromGitHub(CurrentUser currentUser, AdminDtos.DeployRequest request) {
        requireAdmin(currentUser);
        return deployService.startDeploy(request);
    }

    public AdminDtos.DeployJobView deployJob(CurrentUser currentUser, String jobId) {
        requireAdmin(currentUser);
        return deployService.job(jobId);
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || !"ADMIN".equals(currentUser.role())) {
            throw new IllegalArgumentException("Admin permission required");
        }
    }

    private AdminDtos.AdminUserView toUserView(User user) {
        return new AdminDtos.AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
