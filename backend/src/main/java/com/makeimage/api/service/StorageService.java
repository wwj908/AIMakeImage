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
    }

    public Path generatedPath(String filename) {
        return root.resolve("generated").resolve(filename).normalize();
    }

    public String publicGeneratedUrl(String filename) {
        return resolvePublicBaseUrl() + "/files/generated/" + filename;
    }

    public String saveUpload(MultipartFile file) throws IOException {
        String extension = extensionOf(file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        Path target = root.resolve("uploads").resolve(filename).normalize();
        file.transferTo(target);
        return resolvePublicBaseUrl() + "/files/uploads/" + filename;
    }

    public Resource load(String folder, String filename) throws MalformedURLException {
        if (!folder.equals("generated") && !folder.equals("uploads")) {
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
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (Exception ignored) {
            return properties.getPublicBaseUrl();
        }
    }
}
