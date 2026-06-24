package com.makeimage.api.service;

import com.makeimage.api.dto.AdminDtos;
import com.makeimage.api.entity.OpenAiProvider;
import com.makeimage.api.repository.OpenAiProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OpenAiProviderService {
    private final OpenAiProviderRepository repository;

    public OpenAiProviderService(OpenAiProviderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<ProviderConfig> activeProvider() {
        Optional<OpenAiProvider> provider = repository.findFirstByEnabledTrueOrderBySortOrderAscIdAsc();
        if (provider.isPresent()) {
            OpenAiProvider value = provider.get();
            if (usable(value)) {
                return Optional.of(toConfig(value));
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.OpenAiProviderView> allForAdmin() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public List<AdminDtos.OpenAiProviderView> update(List<AdminDtos.OpenAiProviderRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("OpenAI 渠道不能为空");
        }

        Map<Long, OpenAiProvider> existing = repository.findAll().stream()
                .filter(provider -> provider.getId() != null)
                .collect(Collectors.toMap(OpenAiProvider::getId, Function.identity()));
        Set<Long> keptIds = new HashSet<>();

        for (int index = 0; index < requests.size(); index++) {
            AdminDtos.OpenAiProviderRequest request = requests.get(index);
            OpenAiProvider provider = request.id() == null
                    ? new OpenAiProvider()
                    : existing.getOrDefault(request.id(), new OpenAiProvider());

            provider.setName(request.name());
            provider.setBaseUrl(request.baseUrl());
            provider.setModel(request.model());
            provider.setEnabled(Boolean.TRUE.equals(request.enabled()));
            provider.setSortOrder(request.sortOrder() == null ? index + 1 : request.sortOrder());

            String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
            if (provider.getId() == null || (!apiKey.isBlank() && !apiKey.contains("******"))) {
                provider.setApiKey(apiKey);
            }

            validate(provider);
            OpenAiProvider saved = repository.save(provider);
            keptIds.add(saved.getId());
        }

        existing.values().stream()
                .filter(provider -> !keptIds.contains(provider.getId()))
                .forEach(repository::delete);

        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .sorted(Comparator.comparing(OpenAiProvider::getSortOrder).thenComparing(OpenAiProvider::getId))
                .map(this::toView)
                .toList();
    }

    private void validate(OpenAiProvider provider) {
        if (!provider.isEnabled()) {
            return;
        }
        if (!usable(provider)) {
            throw new IllegalArgumentException("启用的 OpenAI 渠道必须填写 Base URL、API Key 和模型");
        }
    }

    private boolean usable(OpenAiProvider provider) {
        return provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank()
                && provider.getApiKey() != null && !provider.getApiKey().isBlank()
                && provider.getModel() != null && !provider.getModel().isBlank();
    }

    private AdminDtos.OpenAiProviderView toView(OpenAiProvider provider) {
        return new AdminDtos.OpenAiProviderView(
                provider.getId(),
                provider.getName(),
                provider.getBaseUrl(),
                mask(provider.getApiKey()),
                provider.getModel(),
                provider.isEnabled(),
                provider.getSortOrder()
        );
    }

    private ProviderConfig toConfig(OpenAiProvider provider) {
        return new ProviderConfig(provider.getName(), provider.getBaseUrl(), provider.getApiKey(), provider.getModel());
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

    public record ProviderConfig(String name, String baseUrl, String apiKey, String model) {
    }
}
