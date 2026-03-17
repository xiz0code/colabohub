package com.colaborapp.reports.web.dto;

import java.time.LocalDate;
import java.util.List;

public record MarketPayoutsTodayResponse(
        Long marketId,
        String marketName,
        LocalDate businessDate,
        List<StorePayoutSummary> stores) {
}
