package com.colaborapp.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.commissions.domain.CommissionRule;
import com.colaborapp.commissions.domain.CommissionRuleScope;
import com.colaborapp.commissions.domain.CommissionType;
import com.colaborapp.commissions.repository.CommissionRuleRepository;
import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.repository.UfDailyValueRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.settings.domain.AppSetting;
import com.colaborapp.settings.repository.AppSettingRepository;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class CommissionSettingsServiceTest {

    @Mock
    private CommissionRuleRepository commissionRuleRepository;

    @Mock
    private UfDailyValueRepository ufDailyValueRepository;

    @Mock
    private MarketRepository marketRepository;

    @Mock
    private CurrentTenantProvider currentTenantProvider;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private UfSyncService ufSyncService;

    @InjectMocks
    private CommissionSettingsService commissionSettingsService;

    private Tenant tenant;
    private Market market;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(10L);
        market.setTenant(tenant);
        market.setName("Sakura Store");
    }

    @Test
    void shouldUpdateMarketUfValue() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(10L));
        when(marketRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(market));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.FIXED, 10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, 10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.FIXED))
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.PERCENTAGE))
                .thenReturn(Optional.empty());
        when(appSettingRepository.findByTenantIdAndSettingKey(1L, "useDynamicFixedCommission")).thenReturn(Optional.empty());

        var response = commissionSettingsService.updateMarketUfValue(new BigDecimal("36500.00"));

        assertThat(response.ufValue()).isEqualByComparingTo("36500.00");
        assertThat(response.ufUpdatedAt()).isNotNull();
        assertThat(response.ufManualOverride()).isTrue();
        assertThat(market.getUfValue()).isEqualByComparingTo("36500.00");
        assertThat(market.isUfManualOverride()).isTrue();
    }

    @Test
    void shouldResetMarketUfToAutomatic() {
        market.setUfValue(new BigDecimal("36500.00"));
        market.setUfUpdatedAt(Instant.parse("2026-03-16T12:00:00Z"));
        market.setUfManualOverride(true);

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(accessControlService.currentMarketIds()).thenReturn(List.of(10L));
        when(marketRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(market));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.FIXED, 10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, 10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.FIXED))
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.PERCENTAGE))
                .thenReturn(Optional.empty());
        when(appSettingRepository.findByTenantIdAndSettingKey(1L, "useDynamicFixedCommission")).thenReturn(Optional.empty());
        when(ufSyncService.syncMarketUf(market)).thenReturn(new BigDecimal("37200.00"));

        var response = commissionSettingsService.resetMarketUfToAutomatic();

        assertThat(response.ufManualOverride()).isFalse();
        assertThat(market.isUfManualOverride()).isFalse();
        assertThat(market.getUfValue()).isEqualByComparingTo("37200.00");
    }

    @Test
    void shouldRecoverOperationalUfAutomaticallyWhenMarketIsAutomatic() {
        market.setUfValue(null);
        market.setUfManualOverride(false);
        when(ufSyncService.syncMarketUf(market)).thenReturn(new BigDecimal("38123.00"));

        BigDecimal resolved = commissionSettingsService.ensureOperationalUfValue(market);

        assertThat(resolved).isEqualByComparingTo("38123.00");
    }

    @Test
    void shouldFailOperationalUfWhenAutomaticSyncFails() {
        market.setUfValue(null);
        market.setUfManualOverride(false);
        when(ufSyncService.syncMarketUf(market)).thenThrow(new RuntimeException("mindicador unavailable"));

        assertThatThrownBy(() -> commissionSettingsService.ensureOperationalUfValue(market))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No pudimos sincronizar automaticamente la UF para esta Tienda. Intenta nuevamente en unos minutos o define la UF manualmente.");
    }

    @Test
    void shouldUpdateMarketGlobalPromotionOverride() {
        CommissionRule fixedRule = new CommissionRule();
        fixedRule.setCommissionValue(new BigDecimal("0.004"));
        CommissionRule percentageRule = new CommissionRule();
        percentageRule.setCommissionValue(new BigDecimal("0.020"));

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(market));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.FIXED, 10L))
                .thenReturn(Optional.of(fixedRule))
                .thenReturn(Optional.of(fixedRule));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketId(1L, CommissionRuleScope.MARKET, CommissionType.PERCENTAGE, 10L))
                .thenReturn(Optional.of(percentageRule))
                .thenReturn(Optional.of(percentageRule));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.FIXED))
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.PERCENTAGE))
                .thenReturn(Optional.empty());
        when(appSettingRepository.findByTenantIdAndSettingKey(1L, "useDynamicFixedCommission")).thenReturn(Optional.empty());

        var response = commissionSettingsService.updateMarketSettings(
                10L,
                true,
                new BigDecimal("0.004"),
                new BigDecimal("0.020"),
                true,
                new BigDecimal("25.00"));

        assertThat(response.globalPromotionEnabled()).isTrue();
        assertThat(response.globalPromotionPercentage()).isEqualByComparingTo("25.00");
        assertThat(market.isGlobalPromotionEnabled()).isTrue();
        assertThat(market.getGlobalPromotionPercentage()).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldRejectInvalidGlobalPromotionPercentage() {
        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(marketRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(market));

        assertThatThrownBy(() -> commissionSettingsService.updateMarketSettings(
                10L,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ingresa un porcentaje valido para activar la promocion global.");

        verify(commissionRuleRepository, never()).save(org.mockito.ArgumentMatchers.any(CommissionRule.class));
    }

    @Test
    void shouldPersistDynamicFixedCommissionSetting() {
        var ufDailyValue = new com.colaborapp.sales.domain.UfDailyValue();
        ufDailyValue.setUfValue(new BigDecimal("36500.00"));
        ufDailyValue.setEffectiveDate(java.time.LocalDate.now());

        when(currentTenantProvider.getCurrentTenant()).thenReturn(tenant);
        when(ufDailyValueRepository.findByTenantIdAndEffectiveDate(1L, java.time.LocalDate.now())).thenReturn(Optional.of(ufDailyValue));
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.FIXED))
                .thenReturn(Optional.empty());
        when(commissionRuleRepository.findByTenantIdAndScopeAndTypeAndMarketIsNullAndStoreIsNull(1L, CommissionRuleScope.GLOBAL, CommissionType.PERCENTAGE))
                .thenReturn(Optional.empty());
        when(appSettingRepository.findByTenantIdAndSettingKey(1L, "useDynamicFixedCommission")).thenReturn(Optional.of(new AppSetting()));

        var response = commissionSettingsService.updateGlobalCommissionSettings(
                new BigDecimal("0.00169"),
                new BigDecimal("0.0079"),
                false);

        assertThat(response.useDynamicFixedCommission()).isFalse();
        verify(appSettingRepository).save(org.mockito.ArgumentMatchers.any(AppSetting.class));
    }
}
