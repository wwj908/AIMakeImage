package com.makeimage.api.repository;

import com.makeimage.api.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findByKey(String key);

    List<SystemSetting> findByKeyIn(Collection<String> keys);
}
