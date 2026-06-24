package com.makeimage.api.service;

import com.makeimage.api.entity.SystemSetting;
import com.makeimage.api.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {
    public static final List<String> KEYS = List.of(
            "system.name",
            "system.logoUrl",
            "mail.username",
            "mail.password"
    );

    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    public String value(String key) {
        return repository.findByKey(key)
                .map(SystemSetting::getValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(defaultValue(key));
    }

    public Map<String, String> allForAdmin() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = value(key);
            values.put(key, isSecretKey(key) ? mask(value) : value);
        }
        return values;
    }

    public Map<String, String> publicSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("system.name", value("system.name"));
        values.put("system.logoUrl", value("system.logoUrl"));
        return values;
    }

    @Transactional
    public Map<String, String> update(Map<String, String> request) {
        for (String key : KEYS) {
            if (!request.containsKey(key)) {
                continue;
            }
            String value = request.get(key);
            if (isSecretKey(key) && (value == null || value.isBlank() || value.startsWith("******"))) {
                continue;
            }
            SystemSetting setting = repository.findByKey(key).orElseGet(SystemSetting::new);
            setting.setKey(key);
            setting.setValue(value == null ? "" : value.trim());
            repository.save(setting);
        }
        return allForAdmin();
    }

    private String defaultValue(String key) {
        return switch (key) {
            case "system.name" -> "MakeImage AI";
            case "system.logoUrl" -> "/image.png";
            default -> "";
        };
    }

    private boolean isSecretKey(String key) {
        return key.endsWith("password") || key.endsWith("apiKey");
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "******";
        }
        return value.substring(0, 4) + "******" + value.substring(value.length() - 4);
    }
}
