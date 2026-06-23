package com.makeimage.api.controller;

import com.makeimage.api.dto.ApiResponse;
import com.makeimage.api.dto.ArtworkDtos;
import com.makeimage.api.service.ArtworkService;
import com.makeimage.api.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ArtworkController {
    private final ArtworkService artworkService;

    public ArtworkController(ArtworkService artworkService) {
        this.artworkService = artworkService;
    }

    @PostMapping("/artworks/generate")
    public ApiResponse<ArtworkDtos.ArtworkView> generate(@Valid @RequestBody ArtworkDtos.GenerateRequest request) throws Exception {
        return ApiResponse.ok("生成成功", artworkService.generate(SecurityUtils.currentUser(), request));
    }

    @PostMapping("/artworks/edit")
    public ApiResponse<ArtworkDtos.ArtworkView> edit(
            @RequestPart("image") MultipartFile image,
            @Valid @RequestPart("data") ArtworkDtos.EditRequest request
    ) throws Exception {
        return ApiResponse.ok("修改成功", artworkService.edit(SecurityUtils.currentUser(), image, request));
    }

    @GetMapping("/artworks/me")
    public ApiResponse<Page<ArtworkDtos.ArtworkView>> myWorks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.ok(artworkService.myWorks(SecurityUtils.currentUser(), pageRequest(page, size)));
    }

    @RequestMapping(value = "/artworks/{id}/publish", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ApiResponse<ArtworkDtos.ArtworkView> publish(@PathVariable Long id, @RequestBody ArtworkDtos.PublishRequest request) {
        return ApiResponse.ok(artworkService.setPublic(SecurityUtils.currentUser(), id, Boolean.TRUE.equals(request.publicWork())));
    }

    @GetMapping("/public/artworks")
    public ApiResponse<Page<ArtworkDtos.ArtworkView>> publicWorks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.ok(artworkService.publicWorks(SecurityUtils.currentUserOrNull(), pageRequest(page, size)));
    }

    @PostMapping("/public/artworks/{id}/download")
    public ApiResponse<ArtworkDtos.ArtworkView> download(@PathVariable Long id) {
        return ApiResponse.ok(artworkService.addDownload(id));
    }

    @PostMapping("/artworks/{id}/like")
    public ApiResponse<ArtworkDtos.ArtworkView> like(@PathVariable Long id) {
        return ApiResponse.ok(artworkService.toggleLike(SecurityUtils.currentUser(), id));
    }

    @PostMapping("/artworks/{id}/favorite")
    public ApiResponse<ArtworkDtos.ArtworkView> favorite(@PathVariable Long id) {
        return ApiResponse.ok(artworkService.toggleFavorite(SecurityUtils.currentUser(), id));
    }

    @GetMapping("/public/artworks/{id}/comments")
    public ApiResponse<java.util.List<ArtworkDtos.CommentView>> comments(@PathVariable Long id) {
        return ApiResponse.ok(artworkService.comments(id));
    }

    @PostMapping("/artworks/{id}/comments")
    public ApiResponse<ArtworkDtos.CommentView> comment(
            @PathVariable Long id,
            @Valid @RequestBody ArtworkDtos.CommentRequest request
    ) {
        return ApiResponse.ok(artworkService.addComment(SecurityUtils.currentUser(), id, request));
    }

    private PageRequest pageRequest(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 40);
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
