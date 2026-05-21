package com.colaborapp.closings.web;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.closings.service.MonthlyClosingService;
import com.colaborapp.closings.web.dto.MonthlyClosingResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/markets/{marketId}/closings")
@RequiredArgsConstructor
public class MonthlyClosingController {

    private final MonthlyClosingService monthlyClosingService;

    @PostMapping("/monthly")
    @PreAuthorize("@accessControl.canManageClosings(#marketId)")
    public MonthlyClosingResponse closeMonthly(
            @PathVariable Long marketId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Authentication authentication) {
        return monthlyClosingService.closeMonth(marketId, month, resolveActor(authentication));
    }

    @GetMapping("/monthly/{month}")
    @PreAuthorize("@accessControl.canAccessMarket(#marketId)")
    public MonthlyClosingResponse getMonthlyClosing(
            @PathVariable Long marketId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return monthlyClosingService.getClosing(marketId, month);
    }

    @GetMapping("/monthly/preview")
    @PreAuthorize("@accessControl.canAccessMarket(#marketId)")
    public MonthlyClosingResponse previewMonthly(
            @PathVariable Long marketId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return monthlyClosingService.previewMonth(marketId, month);
    }

    private String resolveActor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "system"
                : authentication.getName();
    }
}
