package com.makeimage.api.controller;

import com.makeimage.api.dto.AdminDtos;
import com.makeimage.api.dto.ApiResponse;
import com.makeimage.api.service.AdminService;
import com.makeimage.api.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminDtos.StatsView> stats() {
        return ApiResponse.ok(adminService.stats(SecurityUtils.currentUser()));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminDtos.AdminUserView>> users() {
        return ApiResponse.ok(adminService.users(SecurityUtils.currentUser()));
    }

    @PatchMapping("/users/{id}/role")
    public ApiResponse<AdminDtos.AdminUserView> role(@PathVariable Long id, @Valid @RequestBody AdminDtos.RoleRequest request) {
        return ApiResponse.ok(adminService.updateRole(SecurityUtils.currentUser(), id, request.role()));
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, String>> settings() {
        return ApiResponse.ok(adminService.settings(SecurityUtils.currentUser()));
    }

    @PutMapping("/settings")
    public ApiResponse<Map<String, String>> updateSettings(@RequestBody AdminDtos.SettingsRequest request) {
        return ApiResponse.ok(adminService.updateSettings(SecurityUtils.currentUser(), request.settings()));
    }

    @GetMapping("/openai-providers")
    public ApiResponse<List<AdminDtos.OpenAiProviderView>> openAiProviders() {
        return ApiResponse.ok(adminService.openAiProviders(SecurityUtils.currentUser()));
    }

    @PutMapping("/openai-providers")
    public ApiResponse<List<AdminDtos.OpenAiProviderView>> updateOpenAiProviders(@RequestBody AdminDtos.OpenAiProvidersRequest request) {
        return ApiResponse.ok(adminService.updateOpenAiProviders(SecurityUtils.currentUser(), request.providers()));
    }

    @PostMapping("/settings/logo")
    public ApiResponse<Map<String, String>> uploadLogo(@RequestPart("image") MultipartFile image) throws Exception {
        return ApiResponse.ok(adminService.uploadLogo(SecurityUtils.currentUser(), image));
    }

    @PostMapping("/thumbnails/regenerate")
    public ApiResponse<Integer> regenerateThumbnails() throws Exception {
        return ApiResponse.ok(adminService.regenerateThumbnails(SecurityUtils.currentUser()));
    }
}
