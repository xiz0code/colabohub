package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.closings.domain.MonthlyClosing;
import com.colaborapp.closings.domain.MonthlyClosingCollaborator;
import com.colaborapp.closings.repository.MonthlyClosingCollaboratorRepository;
import com.colaborapp.closings.repository.MonthlyClosingRepository;
import com.colaborapp.closings.web.dto.MonthlyClosingCollaboratorResponse;
import com.colaborapp.closings.web.dto.MonthlyClosingResponse;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;

@Service
public class MonthlyClosingService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyClosingService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final MonthlyClosingRepository monthlyClosingRepository;
    private final MonthlyClosingCollaboratorRepository monthlyClosingCollaboratorRepository;
    private final MarketRepository marketRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final CollaboratorSalesSummaryService collaboratorSalesSummaryService;
    private final CollaboratorClosingEmailService collaboratorClosingEmailService;
    private final ZoneId businessZone;

    public MonthlyClosingService(
            MonthlyClosingRepository monthlyClosingRepository,
            MonthlyClosingCollaboratorRepository monthlyClosingCollaboratorRepository,
            MarketRepository marketRepository,
            CurrentTenantProvider currentTenantProvider,
            CollaboratorSalesSummaryService collaboratorSalesSummaryService,
            CollaboratorClosingEmailService collaboratorClosingEmailService,
            @Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.monthlyClosingRepository = monthlyClosingRepository;
        this.monthlyClosingCollaboratorRepository = monthlyClosingCollaboratorRepository;
        this.marketRepository = marketRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.collaboratorSalesSummaryService = collaboratorSalesSummaryService;
        this.collaboratorClosingEmailService = collaboratorClosingEmailService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional
    public MonthlyClosingResponse closeMonth(Long marketId, YearMonth month, String closedBy) {
        YearMonth effectiveMonth = month == null ? YearMonth.now(businessZone) : month;
        LocalDate closingMonth = effectiveMonth.atDay(1);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        return monthlyClosingRepository.findByMarketIdAndClosingMonthWithMarket(tenantId, marketId, closingMonth)
                .map(this::toResponse)
                .orElseGet(() -> createClosing(marketId, effectiveMonth, closedBy));
    }

    @Transactional(readOnly = true)
    public MonthlyClosingResponse getClosing(Long marketId, YearMonth month) {
        YearMonth effectiveMonth = month == null ? YearMonth.now(businessZone) : month;
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        MonthlyClosing closing = monthlyClosingRepository.findByMarketIdAndClosingMonthWithMarket(
                        tenantId,
                        marketId,
                        effectiveMonth.atDay(1))
                .orElseThrow(() -> new ResourceNotFoundException("Todavia no existe un cierre mensual para esa tienda en el mes seleccionado."));
        return toResponse(closing);
    }

    private MonthlyClosingResponse createClosing(Long marketId, YearMonth month, String closedBy) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        Instant startAt = month.atDay(1).atStartOfDay(businessZone).toInstant();
        Instant endAt = month.plusMonths(1).atDay(1).atStartOfDay(businessZone).toInstant();
        List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries =
                collaboratorSalesSummaryService.summarizeByMarketAndPeriod(marketId, startAt, endAt);

        MonthlyClosing closing = new MonthlyClosing();
        closing.setMarket(market);
        closing.setClosingMonth(month.atDay(1));
        closing.setSaleCount(collaboratorSummaries.stream().mapToLong(CollaboratorSalesSummaryService.CollaboratorSummary::saleCount).sum());
        closing.setTotalSalesAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalSalesAmount).toList()));
        closing.setTotalCommissionAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalCommissionAmount).toList()));
        closing.setTotalNetAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalNetAmount).toList()));
        closing.setTotalIvaAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalIvaAmount).toList()));
        closing.setTotalIvaToPayAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::ivaToPayAmount).toList()));
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(closedBy);
        monthlyClosingRepository.save(closing);

        List<MonthlyClosingCollaborator> collaborators = collaboratorSummaries.stream()
                .map(summary -> toCollaboratorRow(closing, summary))
                .toList();
        if (!collaborators.isEmpty()) {
            monthlyClosingCollaboratorRepository.saveAll(collaborators);
        }

        for (CollaboratorSalesSummaryService.CollaboratorSummary summary : collaboratorSummaries) {
            try {
                collaboratorClosingEmailService.sendMonthlySummary(market.getName(), month.atDay(1), summary);
            } catch (Exception exception) {
                log.warn("Monthly closing email could not be sent to collaborator {} for market {} and month {}.",
                        summary.collaboratorEmail(),
                        marketId,
                        month,
                        exception);
            }
        }

        return toResponse(closing, collaborators);
    }

    private MonthlyClosingCollaborator toCollaboratorRow(
            MonthlyClosing closing,
            CollaboratorSalesSummaryService.CollaboratorSummary summary) {
        MonthlyClosingCollaborator collaborator = new MonthlyClosingCollaborator();
        collaborator.setMonthlyClosing(closing);
        collaborator.setCollaboratorUserId(summary.collaboratorUserId());
        collaborator.setCollaboratorNameSnapshot(summary.collaboratorName());
        collaborator.setCollaboratorEmailSnapshot(summary.collaboratorEmail());
        collaborator.setFactura(summary.factura());
        collaborator.setSaleCount(summary.saleCount());
        collaborator.setTotalItems(summary.totalItems());
        collaborator.setTotalSalesAmount(summary.totalSalesAmount());
        collaborator.setTotalCommissionAmount(summary.totalCommissionAmount());
        collaborator.setTotalNetAmount(summary.totalNetAmount());
        collaborator.setTotalIvaAmount(summary.totalIvaAmount());
        collaborator.setIvaToPayAmount(summary.ivaToPayAmount());
        return collaborator;
    }

    private MonthlyClosingResponse toResponse(MonthlyClosing closing) {
        List<MonthlyClosingCollaborator> collaborators =
                monthlyClosingCollaboratorRepository.findByMonthlyClosingIdOrderByCollaboratorNameSnapshotAsc(closing.getId());
        return toResponse(closing, collaborators);
    }

    private MonthlyClosingResponse toResponse(MonthlyClosing closing, List<MonthlyClosingCollaborator> collaborators) {
        return new MonthlyClosingResponse(
                closing.getMarket().getId(),
                closing.getMarket().getName(),
                closing.getClosingMonth(),
                closing.getSaleCount(),
                closing.getTotalSalesAmount(),
                closing.getTotalCommissionAmount(),
                closing.getTotalNetAmount(),
                closing.getTotalIvaAmount(),
                closing.getTotalIvaToPayAmount(),
                closing.getClosedAt(),
                closing.getClosedBy(),
                collaborators.stream()
                        .map(collaborator -> new MonthlyClosingCollaboratorResponse(
                                collaborator.getCollaboratorUserId(),
                                collaborator.getCollaboratorNameSnapshot(),
                                collaborator.getCollaboratorEmailSnapshot(),
                                collaborator.isFactura(),
                                collaborator.getSaleCount(),
                                collaborator.getTotalItems(),
                                collaborator.getTotalSalesAmount(),
                                collaborator.getTotalCommissionAmount(),
                                collaborator.getTotalNetAmount(),
                                collaborator.getTotalIvaAmount(),
                                collaborator.getIvaToPayAmount()))
                        .toList());
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(ZERO, BigDecimal::add);
    }
}
