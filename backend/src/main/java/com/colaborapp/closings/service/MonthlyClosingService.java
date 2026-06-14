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
import com.colaborapp.closings.web.dto.ClosingPaymentMethodSummaryResponse;
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
    private final ClosingPaymentMethodSummaryService closingPaymentMethodSummaryService;
    private final CollaboratorClosingEmailService collaboratorClosingEmailService;
    private final ZoneId businessZone;

    public MonthlyClosingService(
            MonthlyClosingRepository monthlyClosingRepository,
            MonthlyClosingCollaboratorRepository monthlyClosingCollaboratorRepository,
            MarketRepository marketRepository,
            CurrentTenantProvider currentTenantProvider,
            CollaboratorSalesSummaryService collaboratorSalesSummaryService,
            ClosingPaymentMethodSummaryService closingPaymentMethodSummaryService,
            CollaboratorClosingEmailService collaboratorClosingEmailService,
            @Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.monthlyClosingRepository = monthlyClosingRepository;
        this.monthlyClosingCollaboratorRepository = monthlyClosingCollaboratorRepository;
        this.marketRepository = marketRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.collaboratorSalesSummaryService = collaboratorSalesSummaryService;
        this.closingPaymentMethodSummaryService = closingPaymentMethodSummaryService;
        this.collaboratorClosingEmailService = collaboratorClosingEmailService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional
    public MonthlyClosingResponse closeMonth(Long marketId, YearMonth month, String closedBy) {
        YearMonth effectiveMonth = month == null ? YearMonth.now(businessZone) : month;
        return persistClosing(marketId, effectiveMonth, closedBy);
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

    @Transactional(readOnly = true)
    public MonthlyClosingResponse previewMonth(Long marketId, YearMonth month) {
        YearMonth effectiveMonth = month == null ? YearMonth.now(businessZone) : month;
        ClosingSnapshot snapshot = buildSnapshot(marketId, effectiveMonth);
        return toResponse(snapshot.market(), effectiveMonth.atDay(1), null, "Vista previa", snapshot.collaboratorSummaries());
    }

    private MonthlyClosingResponse persistClosing(Long marketId, YearMonth month, String closedBy) {
        ClosingSnapshot snapshot = buildSnapshot(marketId, month);
        LocalDate closingMonth = month.atDay(1);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        MonthlyClosing closing = monthlyClosingRepository.findByMarketIdAndClosingMonthWithMarket(tenantId, marketId, closingMonth)
                .orElseGet(MonthlyClosing::new);

        closing.setMarket(snapshot.market());
        closing.setClosingMonth(closingMonth);
        applyTotals(closing, snapshot.collaboratorSummaries());
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(closedBy);
        monthlyClosingRepository.save(closing);

        monthlyClosingCollaboratorRepository.deleteByMonthlyClosingId(closing.getId());

        List<MonthlyClosingCollaborator> collaborators = snapshot.collaboratorSummaries().stream()
                .map(summary -> toCollaboratorRow(closing, summary))
                .toList();
        if (!collaborators.isEmpty()) {
            monthlyClosingCollaboratorRepository.saveAll(collaborators);
        }

        for (CollaboratorSalesSummaryService.CollaboratorSummary summary : snapshot.collaboratorSummaries()) {
            try {
                collaboratorClosingEmailService.sendMonthlySummary(snapshot.market().getName(), month.atDay(1), summary);
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

    private ClosingSnapshot buildSnapshot(Long marketId, YearMonth month) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        Instant startAt = month.atDay(1).atStartOfDay(businessZone).toInstant();
        Instant endAt = month.plusMonths(1).atDay(1).atStartOfDay(businessZone).toInstant();
        List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries =
                collaboratorSalesSummaryService.summarizeByMarketAndPeriod(marketId, startAt, endAt);
        List<ClosingPaymentMethodSummaryResponse> paymentMethodSummaries =
                closingPaymentMethodSummaryService.summarizeByMarketAndPeriod(marketId, startAt, endAt);

        return new ClosingSnapshot(market, collaboratorSummaries, paymentMethodSummaries);
    }

    private void applyTotals(MonthlyClosing closing, List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries) {
        closing.setSaleCount(collaboratorSummaries.stream().mapToLong(CollaboratorSalesSummaryService.CollaboratorSummary::saleCount).sum());
        closing.setTotalSalesAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalSalesAmount).toList()));
        closing.setTotalCommissionAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalCommissionAmount).toList()));
        closing.setTotalNetAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalNetAmount).toList()));
        closing.setTotalIvaAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalIvaAmount).toList()));
        closing.setTotalIvaToPayAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::ivaToPayAmount).toList()));
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
                buildPaymentMethodSummaries(closing.getMarket().getId(), YearMonth.from(closing.getClosingMonth())),
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

    private MonthlyClosingResponse toResponse(
            Market market,
            LocalDate closingMonth,
            Instant closedAt,
            String closedBy,
            List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries) {
        return new MonthlyClosingResponse(
                market.getId(),
                market.getName(),
                closingMonth,
                collaboratorSummaries.stream().mapToLong(CollaboratorSalesSummaryService.CollaboratorSummary::saleCount).sum(),
                sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalSalesAmount).toList()),
                sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalCommissionAmount).toList()),
                sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalNetAmount).toList()),
                sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalIvaAmount).toList()),
                sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::ivaToPayAmount).toList()),
                closedAt,
                closedBy,
                buildPaymentMethodSummaries(market.getId(), YearMonth.from(closingMonth)),
                collaboratorSummaries.stream()
                        .map(summary -> new MonthlyClosingCollaboratorResponse(
                                summary.collaboratorUserId(),
                                summary.collaboratorName(),
                                summary.collaboratorEmail(),
                                summary.factura(),
                                summary.saleCount(),
                                summary.totalItems(),
                                summary.totalSalesAmount(),
                                summary.totalCommissionAmount(),
                                summary.totalNetAmount(),
                                summary.totalIvaAmount(),
                                summary.ivaToPayAmount()))
                        .toList());
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(ZERO, BigDecimal::add);
    }

    private List<ClosingPaymentMethodSummaryResponse> buildPaymentMethodSummaries(Long marketId, YearMonth month) {
        Instant startAt = month.atDay(1).atStartOfDay(businessZone).toInstant();
        Instant endAt = month.plusMonths(1).atDay(1).atStartOfDay(businessZone).toInstant();
        return closingPaymentMethodSummaryService.summarizeByMarketAndPeriod(marketId, startAt, endAt);
    }

    private record ClosingSnapshot(
            Market market,
            List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries,
            List<ClosingPaymentMethodSummaryResponse> paymentMethodSummaries) {
    }
}
