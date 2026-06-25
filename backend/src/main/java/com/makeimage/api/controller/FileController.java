package com.makeimage.api.controller;

import com.makeimage.api.service.StorageService;
import com.makeimage.api.service.ThumbnailService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/files")
public class FileController {
    private final StorageService storageService;
    private final ThumbnailService thumbnailService;

    public FileController(StorageService storageService, ThumbnailService thumbnailService) {
        this.storageService = storageService;
        this.thumbnailService = thumbnailService;
    }

    @GetMapping("/{folder}/{filename}")
    public ResponseEntity<Resource> file(@PathVariable String folder, @PathVariable String filename) throws Exception {
        if ("thumbnails".equals(folder)) {
            thumbnailService.ensureThumbnailFromThumbnailName(filename);
        }
        Resource resource = storageService.load(folder, filename);
        MediaType contentType = mediaType(filename);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS))
                .contentType(contentType)
                .body(resource);
    }

    private MediaType mediaType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_PNG;
    }
}
