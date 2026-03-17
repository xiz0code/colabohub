package com.colaborapp.config.bootstrap;

import org.springframework.stereotype.Component;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentTenantProvider {

    private final TenantRepository tenantRepository;

    public Tenant getCurrentTenant() {
        return tenantRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("No tenant is configured for the application."));
    }
}
