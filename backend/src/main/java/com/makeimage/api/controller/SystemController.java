package com.makeimage.api.controller;

import com.makeimage.api.dto.ApiResponse;
import com.makeimage.api.service.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/system")
public class SystemController {
    private final SystemSettingService systemSettingService;

    public SystemController(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> settings() {
        return ApiResponse.ok(systemSettingService.publicSettings());
    }
}
