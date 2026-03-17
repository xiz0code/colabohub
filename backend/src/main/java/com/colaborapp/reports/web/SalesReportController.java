package com.colaborapp.reports.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import com.colaborapp.reports.service.SalesReportService;
import com.colaborapp.reports.web.dto.DashboardSummaryResponse;
import com.colaborapp.reports.web.dto.SalesTodayDetailsResponse;
import com.colaborapp.reports.web.dto.SalesTodayReportResponse;
import com.colaborapp.reports.web.dto.StoreSalesReportResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/dashboard")
    @PreAuthorize("@accessControl.canAccessOperationalReports()")
    public DashboardSummaryResponse getDashboardSummary() {
        return salesReportService.getDashboardSummary();
    }

    @GetMapping("/today")
    @PreAuthorize("@accessControl.canAccessOperationalReports()")
    public SalesTodayReportResponse getToday() {
        return salesReportService.getTodayReport();
    }

    @GetMapping("/today/details")
    @PreAuthorize("@accessControl.canAccessOperationalReports()")
    public SalesTodayDetailsResponse getTodayDetails() {
        return salesReportService.getTodayDetails();
    }

    @GetMapping("/store/{storeId}")
    @PreAuthorize("@accessControl.canAccessStore(#storeId)")
    public StoreSalesReportResponse getStoreReport(@PathVariable Long storeId) {
        return salesReportService.getStoreReport(storeId);
    }
}
