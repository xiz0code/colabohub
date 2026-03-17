package com.colaborapp.settings.web;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.settings.web.dto.GlobalCommissionSettingsRequest;
import com.colaborapp.settings.web.dto.GlobalFinancialSettingsResponse;
import com.colaborapp.settings.web.dto.MarketCommissionSettingsRequest;
import com.colaborapp.settings.web.dto.MarketFinancialSettingsResponse;
import com.colaborapp.settings.web.dto.UfValueUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CommissionSettingsService commissionSettingsService;

    @GetMapping("/global")
    public GlobalFinancialSettingsResponse getGlobalSettings() {
        var settings = commissionSettingsService.getGlobalSettings();
        return new GlobalFinancialSettingsResponse(
                settings.currentUfValue(),
                settings.ufLastUpdatedAt(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue());
    }

    @PutMapping("/uf")
    public MarketFinancialSettingsResponse updateMarketUfValue(@Valid @RequestBody UfValueUpdateRequest request) {
        var settings = commissionSettingsService.updateMarketUfValue(request.ufValue());
        return new MarketFinancialSettingsResponse(
                settings.marketId(),
                settings.marketName(),
                settings.ufValue(),
                settings.ufUpdatedAt(),
                settings.overrideEnabled(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue(),
                settings.effectiveCommissionUfValue(),
                settings.effectiveCommissionPercentageValue(),
                settings.globalPromotionEnabled(),
                settings.globalPromotionPercentage());
    }

    @PatchMapping("/global/uf")
    public GlobalFinancialSettingsResponse updateUfValue(@Valid @RequestBody UfValueUpdateRequest request) {
        var settings = commissionSettingsService.updateGlobalUfValue(request.ufValue());
        return new GlobalFinancialSettingsResponse(
                settings.currentUfValue(),
                settings.ufLastUpdatedAt(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue());
    }

    @PatchMapping("/global/commissions")
    public GlobalFinancialSettingsResponse updateGlobalCommissions(@Valid @RequestBody GlobalCommissionSettingsRequest request) {
        var settings = commissionSettingsService.updateGlobalCommissionSettings(
                request.commissionUfValue(),
                request.commissionPercentageValue());
        return new GlobalFinancialSettingsResponse(
                settings.currentUfValue(),
                settings.ufLastUpdatedAt(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue());
    }

    @GetMapping("/markets/{marketId}")
    public MarketFinancialSettingsResponse getMarketSettings(@PathVariable Long marketId) {
        var settings = commissionSettingsService.getMarketSettings(marketId);
        return new MarketFinancialSettingsResponse(
                settings.marketId(),
                settings.marketName(),
                settings.ufValue(),
                settings.ufUpdatedAt(),
                settings.overrideEnabled(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue(),
                settings.effectiveCommissionUfValue(),
                settings.effectiveCommissionPercentageValue(),
                settings.globalPromotionEnabled(),
                settings.globalPromotionPercentage());
    }

    @PatchMapping("/markets/{marketId}/commissions")
    public MarketFinancialSettingsResponse updateMarketCommissions(
            @PathVariable Long marketId,
            @Valid @RequestBody MarketCommissionSettingsRequest request) {
        var settings = commissionSettingsService.updateMarketSettings(
                marketId,
                request.overrideEnabled(),
                request.commissionUfValue(),
                request.commissionPercentageValue(),
                request.globalPromotionEnabled(),
                request.globalPromotionPercentage());
        return new MarketFinancialSettingsResponse(
                settings.marketId(),
                settings.marketName(),
                settings.ufValue(),
                settings.ufUpdatedAt(),
                settings.overrideEnabled(),
                settings.globalCommissionUfValue(),
                settings.globalCommissionPercentageValue(),
                settings.effectiveCommissionUfValue(),
                settings.effectiveCommissionPercentageValue(),
                settings.globalPromotionEnabled(),
                settings.globalPromotionPercentage());
    }
}
