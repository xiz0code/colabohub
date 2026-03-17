package com.colaborapp.closings.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.closings.service.DailyClosingService;
import com.colaborapp.closings.web.dto.DailyClosingResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/markets/{marketId}/closings")
@RequiredArgsConstructor
public class DailyClosingController {

    private final DailyClosingService dailyClosingService;

    @PostMapping("/daily")
    @PreAuthorize("@accessControl.canManageClosings(#marketId)")
    public DailyClosingResponse closeDaily(
            @PathVariable Long marketId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return dailyClosingService.closeDay(marketId, date, resolveActor(authentication));
    }

    @GetMapping("/{date}")
    @PreAuthorize("@accessControl.canAccessMarket(#marketId)")
    public DailyClosingResponse getClosing(
            @PathVariable Long marketId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dailyClosingService.getClosing(marketId, date);
    }

    private String resolveActor(Authentication authentication) {
        return authentication == null || authentication.getName() == null || authentication.getName().isBlank()
                ? "system"
                : authentication.getName();
    }
}
