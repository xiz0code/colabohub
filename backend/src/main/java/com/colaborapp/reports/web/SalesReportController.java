package com.colaborapp.reports.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

import com.colaborapp.reports.service.SalesReportService;
import com.colaborapp.reports.web.dto.CollaboratorSalesReportResponse;
import com.colaborapp.reports.web.dto.CommissionRecalculationRequest;
import com.colaborapp.reports.web.dto.CommissionRecalculationResponse;
import com.colaborapp.reports.web.dto.DashboardSummaryResponse;
import com.colaborapp.reports.web.dto.SalesTodayDetailsResponse;
import com.colaborapp.reports.web.dto.SalesTodayReportResponse;
import com.colaborapp.reports.web.dto.StoreSalesReportResponse;
import com.colaborapp.sales.service.SalesCommissionRecalculationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports/sales")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;
    private final SalesCommissionRecalculationService salesCommissionRecalculationService;

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

    @GetMapping("/collaborator/{collaboratorUserId}")
    @PreAuthorize("@accessControl.canAccessOperationalReports()")
    public CollaboratorSalesReportResponse getCollaboratorReport(
            @PathVariable Long collaboratorUserId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        return salesReportService.getCollaboratorSalesReport(collaboratorUserId, dateFrom, dateTo);
    }

    @PostMapping("/commissions/recalculate")
    @PreAuthorize("@accessControl.canViewSalesDashboard()")
    public CommissionRecalculationResponse recalculateCommissions(@Valid @RequestBody CommissionRecalculationRequest request) {
        return salesCommissionRecalculationService.recalculate(request.dateFrom(), request.dateTo());
    }
}
