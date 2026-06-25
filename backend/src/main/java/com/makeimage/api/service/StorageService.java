package com.makeimage.api.service;

import com.makeimage.api.config.AppProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class StorageService {
    private final AppProperties properties;
    private final Path root;

    public StorageService(AppProperties properties) throws IOException {
        this.properties = properties;
        this.root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("generated"));
        Files.createDirectories(root.resolve("uploads"));
        Files.createDirectories(root.resolve("thumbnails"));
    }

    public Path generatedPath(String filename) {
        return root.resolve("generated").resolve(filename).normalize();
    }

    public String publicGeneratedUrl(String filename) {
        return resolvePublicBaseUrl() + "/files/generated/" + filename;
    }

    public String publicThumbnailUrl(String filename) {
        String thumbName = thumbnailName(filename);
        return resolveThumbnailBaseUrl() + "/files/thumbnails/" + thumbName;
    }

    public Path thumbnailPath(String filename) {
        return root.resolve("thumbnails").resolve(thumbnailName(filename)).normalize();
    }

    public String saveUpload(MultipartFile file) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        Path target = root.resolve("uploads").resolve(filename).normalize();
        file.transferTo(target);
        return resolvePublicBaseUrl() + "/files/uploads/" + filename;
    }

    public String saveSystemAsset(MultipartFile file) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        String filename = "system-logo-" + UUID.randomUUID() + extension;
        Path target = root.resolve("uploads").resolve(filename).normalize();
        file.transferTo(target);
        return resolvePublicBaseUrl() + "/files/uploads/" + filename;
    }

    public Resource load(String folder, String filename) throws MalformedURLException {
        if (!folder.equals("generated") && !folder.equals("uploads") && !folder.equals("thumbnails")) {
            throw new IllegalArgumentException("Invalid folder");
        }
        Path file = root.resolve(folder).resolve(filename).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid path");
        }
        return new UrlResource(file.toUri());
    }

    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return ".png";
        }
        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|webp)") ? ext : ".png";
    }

    private String resolvePublicBaseUrl() {
        String configured = properties.getPublicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception ignored) {
            return "http://localhost:8080";
        }
    }

    private String resolveThumbnailBaseUrl() {
        String thumbnailBase = properties.getThumbnailBaseUrl();
        if (thumbnailBase != null && !thumbnailBase.isBlank()) {
            return thumbnailBase;
        }
        return resolvePublicBaseUrl();
    }

    private String thumbnailName(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return filename + ".jpg";
        }
        return filename.substring(0, dot) + ".jpg";
    }
}
