package com.makeimage.api.repository;

import com.makeimage.api.entity.OpenAiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpenAiProviderRepository extends JpaRepository<OpenAiProvider, Long> {
    List<OpenAiProvider> findAllByOrderBySortOrderAscIdAsc();

    Optional<OpenAiProvider> findFirstByEnabledTrueOrderBySortOrderAscIdAsc();
}
