package com.colaborapp.settings.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.commissions.domain.CommissionRule;
import com.colaborapp.commissions.domain.CommissionRuleScope;
import com.colaborapp.commissions.domain.CommissionType;
import com.colaborapp.commissions.repository.CommissionRuleRepository;
import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.domain.UfDailyValue;
import com.colaborapp.sales.repository.UfDailyValueRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommissionSettingsService {

    public static final BigDecimal DEFAULT_COMMISSION_UF = new BigDecimal("0.00169");
    public static final BigDecimal DEFAULT_COMMISSION_PERCENTAGE = new BigDecimal("0.0079");

    private final CommissionRuleRepository commissionRuleRepository;
    private final UfDailyValueRepository ufDailyValueRepository;
    private final MarketRepository marketRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public GlobalFinancialSettings getGlobalSettings() {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        LocalDate businessDate = LocalDate.now();
        UfDailyValue ufValue = ufDailyValueRepository.findByTenantIdAndEffectiveDate(tenantId, businessDate)
                .orElseThrow(() -> new ResourceNotFoundException("UF value is not configured for the current business date."));

        return new GlobalFinancialSettings(
                ufValue.getUfValue(),
                ufValue.getEffectiveDate(),
                getGlobalCommissionUfValue(tenantId),
                getGlobalCommissionPercentageValue(tenantId));
    }

    @Transactional(readOnly = true)
    public MarketFinancialSettings getMarketSettings(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        accessControlService.requireMarketAccess(marketId);
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));
        BigDecimal globalUfCommission = getGlobalCommissionUfValue(tenantId);
        BigDecimal globalPercentageCommission = getGlobalCommissionPercentageValue(tenantId);
        var override = getEffectiveCommissionConfig(marketId);
        boolean hasOverride = commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(
                tenantId,
                CommissionRuleScope.MARKET,
                CommissionType.FIXED,
                marketId).isPresent()
                || commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(
                        tenantId,
                        CommissionRuleScope.MARKET,
                        CommissionType.PERCENTAGE,
                        marketId).isPresent();
        return new MarketFinancialSettings(
                market.getId(),
                market.getName(),
                market.getUfValue(),
                market.getUfUpdatedAt(),
                hasOverride,
                globalUfCommission,
                globalPercentageCommission,
                override.commissionUfValue(),
                override.commissionPercentageValue(),
                market.isGlobalPromotionEnabled(),
                market.getGlobalPromotionPercentage());
    }

    @Transactional
    public GlobalFinancialSettings updateGlobalUfValue(BigDecimal ufValue) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        LocalDate businessDate = LocalDate.now();
        UfDailyValue current = ufDailyValueRepository.findByTenantIdAndEffectiveDate(tenantId, businessDate)
                .orElseGet(() -> {
                    UfDailyValue next = new UfDailyValue();
                    next.setTenant(currentTenantProvider.getCurrentTenant());
                    next.setEffectiveDate(businessDate);
                    return next;
                });
        current.setUfValue(ufValue);
        current.setSource("MANUAL");
        ufDailyValueRepository.save(current);
        return getGlobalSettings();
    }

    @Transactional
    public MarketFinancialSettings updateMarketUfValue(BigDecimal ufValue) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_MARKET);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.size() != 1) {
            throw new BusinessException("Necesitas una Tienda activa para actualizar el valor UF.");
        }

        Market market = marketRepository.findByIdAndTenantId(marketIds.getFirst(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));
        market.setUfValue(ufValue);
        market.setUfUpdatedAt(Instant.now());
        return getMarketSettings(market.getId());
    }

    @Transactional
    public GlobalFinancialSettings updateGlobalCommissionSettings(BigDecimal commissionUfValue, BigDecimal commissionPercentageValue) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        upsertCommissionRule(tenantId, null, CommissionRuleScope.GLOBAL, CommissionType.FIXED, commissionUfValue);
        upsertCommissionRule(tenantId, null, CommissionRuleScope.GLOBAL, CommissionType.PERCENTAGE, commissionPercentageValue);
        return getGlobalSettings();
    }

    @Transactional
    public MarketFinancialSettings updateMarketCommissionSettings(Long marketId, boolean overrideEnabled, BigDecimal commissionUfValue, BigDecimal commissionPercentageValue) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireMarketAccess(marketId);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        if (!overrideEnabled) {
            commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(tenantId, CommissionRuleScope.MARKET, CommissionType.FIXED, marketId)
                    .ifPresent(commissionRuleRepository::delete);
            commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(tenantId, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, marketId)
                    .ifPresent(commissionRuleRepository::delete);
            market.setGlobalPromotionEnabled(false);
            market.setGlobalPromotionPercentage(null);
            return getMarketSettings(marketId);
        }

        upsertCommissionRule(tenantId, market, CommissionRuleScope.MARKET, CommissionType.FIXED, commissionUfValue);
        upsertCommissionRule(tenantId, market, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, commissionPercentageValue);
        return getMarketSettings(marketId);
    }

    @Transactional
    public MarketFinancialSettings updateMarketSettings(
            Long marketId,
            boolean overrideEnabled,
            BigDecimal commissionUfValue,
            BigDecimal commissionPercentageValue,
            boolean globalPromotionEnabled,
            BigDecimal globalPromotionPercentage) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET);
        accessControlService.requireMarketAccess(marketId);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        if (!overrideEnabled) {
            commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(tenantId, CommissionRuleScope.MARKET, CommissionType.FIXED, marketId)
                    .ifPresent(commissionRuleRepository::delete);
            commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(tenantId, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, marketId)
                    .ifPresent(commissionRuleRepository::delete);
        } else {
            upsertCommissionRule(tenantId, market, CommissionRuleScope.MARKET, CommissionType.FIXED, commissionUfValue);
            upsertCommissionRule(tenantId, market, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, commissionPercentageValue);
        }

        if (globalPromotionEnabled && (globalPromotionPercentage == null || globalPromotionPercentage.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Ingresa un porcentaje valido para activar la promocion global.");
        }

        market.setGlobalPromotionEnabled(globalPromotionEnabled);
        market.setGlobalPromotionPercentage(globalPromotionEnabled ? globalPromotionPercentage : null);
        return getMarketSettings(marketId);
    }

    @Transactional(readOnly = true)
    public EffectiveCommissionConfig getEffectiveCommissionConfig(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        BigDecimal globalUf = getGlobalCommissionUfValue(tenantId);
        BigDecimal globalPercentage = getGlobalCommissionPercentageValue(tenantId);

        BigDecimal marketUf = commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(
                tenantId,
                CommissionRuleScope.MARKET,
                CommissionType.FIXED,
                marketId)
                .map(CommissionRule::getCommissionValue)
                .orElse(globalUf);

        BigDecimal marketPercentage = commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(
                tenantId,
                CommissionRuleScope.MARKET,
                CommissionType.PERCENTAGE,
                marketId)
                .map(CommissionRule::getCommissionValue)
                .orElse(globalPercentage);

        return new EffectiveCommissionConfig(marketUf, marketPercentage);
    }

    @Transactional(readOnly = true)
    public Map<Long, EffectiveCommissionConfig> getEffectiveCommissionConfig(List<Long> marketIds) {
        Map<Long, EffectiveCommissionConfig> result = new HashMap<>();
        if (marketIds.isEmpty()) {
            return result;
        }

        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        BigDecimal globalUf = getGlobalCommissionUfValue(tenantId);
        BigDecimal globalPercentage = getGlobalCommissionPercentageValue(tenantId);

        Map<Long, BigDecimal> fixedByMarket = new HashMap<>();
        Map<Long, BigDecimal> percentageByMarket = new HashMap<>();
        commissionRuleRepository.findAllByTenantIdAndScopeAndMarketIdIn(tenantId, CommissionRuleScope.MARKET, marketIds)
                .forEach(rule -> {
                    if (rule.getMarket() == null) {
                        return;
                    }
                    if (rule.getType() == CommissionType.FIXED) {
                        fixedByMarket.put(rule.getMarket().getId(), rule.getCommissionValue());
                    } else if (rule.getType() == CommissionType.PERCENTAGE) {
                        percentageByMarket.put(rule.getMarket().getId(), rule.getCommissionValue());
                    }
                });

        for (Long marketId : marketIds) {
            result.put(marketId, new EffectiveCommissionConfig(
                    fixedByMarket.getOrDefault(marketId, globalUf),
                    percentageByMarket.getOrDefault(marketId, globalPercentage)));
        }
        return result;
    }

    private BigDecimal getGlobalCommissionUfValue(Long tenantId) {
        return commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(
                tenantId,
                CommissionRuleScope.GLOBAL,
                CommissionType.FIXED)
                .map(CommissionRule::getCommissionValue)
                .orElse(DEFAULT_COMMISSION_UF);
    }

    private BigDecimal getGlobalCommissionPercentageValue(Long tenantId) {
        return commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(
                tenantId,
                CommissionRuleScope.GLOBAL,
                CommissionType.PERCENTAGE)
                .map(CommissionRule::getCommissionValue)
                .orElse(DEFAULT_COMMISSION_PERCENTAGE);
    }

    private void upsertCommissionRule(Long tenantId, Market market, CommissionRuleScope scope, CommissionType type, BigDecimal value) {
        CommissionRule rule = (scope == CommissionRuleScope.MARKET && market != null)
                ? commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(tenantId, scope, type, market.getId()).orElseGet(CommissionRule::new)
                : commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(tenantId, scope, type).orElseGet(CommissionRule::new);

        rule.setTenant(currentTenantProvider.getCurrentTenant());
        rule.setScope(scope);
        rule.setType(type);
        rule.setName(scope == CommissionRuleScope.GLOBAL
                ? "GLOBAL_" + type.name()
                : "MARKET_" + market.getId() + "_" + type.name());
        rule.setMarket(market);
        rule.setStore(null);
        rule.setCommissionValue(value);
        rule.setActive(true);
        commissionRuleRepository.save(rule);
    }

    public record EffectiveCommissionConfig(
            BigDecimal commissionUfValue,
            BigDecimal commissionPercentageValue) {
    }

    public record GlobalFinancialSettings(
            BigDecimal currentUfValue,
            LocalDate ufLastUpdatedAt,
            BigDecimal globalCommissionUfValue,
            BigDecimal globalCommissionPercentageValue) {
    }

    public record MarketFinancialSettings(
            Long marketId,
            String marketName,
            BigDecimal ufValue,
            Instant ufUpdatedAt,
            boolean overrideEnabled,
            BigDecimal globalCommissionUfValue,
            BigDecimal globalCommissionPercentageValue,
            BigDecimal effectiveCommissionUfValue,
            BigDecimal effectiveCommissionPercentageValue,
            boolean globalPromotionEnabled,
            BigDecimal globalPromotionPercentage) {
    }
}
