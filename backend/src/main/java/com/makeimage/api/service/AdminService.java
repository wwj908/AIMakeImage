package com.makeimage.api.service;

import com.makeimage.api.dto.AdminDtos;
import com.makeimage.api.entity.User;
import com.makeimage.api.repository.*;
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
    private final StorageService storageService;

    public AdminService(
            UserRepository userRepository,
            ArtworkRepository artworkRepository,
            ArtworkLikeRepository artworkLikeRepository,
            ArtworkFavoriteRepository artworkFavoriteRepository,
            ArtworkCommentRepository artworkCommentRepository,
            SystemSettingService systemSettingService,
            StorageService storageService
    ) {
        this.userRepository = userRepository;
        this.artworkRepository = artworkRepository;
        this.artworkLikeRepository = artworkLikeRepository;
        this.artworkFavoriteRepository = artworkFavoriteRepository;
        this.artworkCommentRepository = artworkCommentRepository;
        this.systemSettingService = systemSettingService;
        this.storageService = storageService;
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
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setRole(role);
        return toUserView(userRepository.saveAndFlush(user));
    }

    public Map<String, String> settings(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return systemSettingService.allForAdmin();
    }

    @Transactional
    public Map<String, String> updateSettings(CurrentUser currentUser, Map<String, String> settings) {
        requireAdmin(currentUser);
        return systemSettingService.update(settings == null ? Map.of() : settings);
    }

    @Transactional
    public Map<String, String> uploadLogo(CurrentUser currentUser, MultipartFile file) throws Exception {
        requireAdmin(currentUser);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传系统 Logo");
        }
        String logoUrl = storageService.saveSystemAsset(file);
        return systemSettingService.update(Map.of("system.logoUrl", logoUrl));
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || !"ADMIN".equals(currentUser.role())) {
            throw new IllegalArgumentException("需要管理员权限");
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
