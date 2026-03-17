package com.colaborapp.reports.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.colaborapp.reports.service.SalesReportService;
import com.colaborapp.reports.web.dto.MarketPayoutsTodayResponse;
import com.colaborapp.reports.web.dto.MarketSalesTodayReportResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports/markets")
@RequiredArgsConstructor
public class MarketReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/{marketId}/sales/today")
    @PreAuthorize("@accessControl.canAccessMarket(#marketId)")
    public MarketSalesTodayReportResponse getMarketToday(@PathVariable Long marketId) {
        return salesReportService.getMarketTodayReport(marketId);
    }

    @GetMapping("/{marketId}/payouts/today")
    @PreAuthorize("@accessControl.canAccessMarket(#marketId)")
    public MarketPayoutsTodayResponse getMarketPayoutsToday(@PathVariable Long marketId) {
        return salesReportService.getMarketPayoutsToday(marketId);
    }
}
