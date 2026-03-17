package com.colaborapp.tenant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.tenant.domain.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findFirstByOrderByIdAsc();

    boolean existsByCodeIgnoreCase(String code);
}
