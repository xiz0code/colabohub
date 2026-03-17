package com.colaborapp.settings.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.settings.domain.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByTenantIdAndSettingKey(Long tenantId, String settingKey);
}
