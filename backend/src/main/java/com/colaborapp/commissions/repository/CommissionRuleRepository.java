package com.colaborapp.commissions.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.colaborapp.commissions.domain.CommissionRule;
import com.colaborapp.commissions.domain.CommissionRuleScope;
import com.colaborapp.commissions.domain.CommissionType;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {

    Optional<CommissionRule> findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(
            Long tenantId,
            CommissionRuleScope scope,
            CommissionType type);

    Optional<CommissionRule> findByTenantIdAndScopeAndTypeAndMarketId(
            Long tenantId,
            CommissionRuleScope scope,
            CommissionType type,
            Long marketId);

    List<CommissionRule> findAllByTenantIdAndScopeAndMarketIdIn(
            Long tenantId,
            CommissionRuleScope scope,
            List<Long> marketIds);
}
