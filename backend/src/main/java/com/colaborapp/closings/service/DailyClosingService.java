package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.closings.domain.DailyClosing;
import com.colaborapp.closings.domain.DailyClosingCollaborator;
import com.colaborapp.closings.domain.DailyClosingStore;
import com.colaborapp.closings.repository.DailyClosingCollaboratorRepository;
import com.colaborapp.closings.repository.DailyClosingRepository;
import com.colaborapp.closings.repository.DailyClosingStoreRepository;
import com.colaborapp.closings.web.dto.DailyClosingResponse;
import com.colaborapp.closings.web.dto.DailyClosingStoreResponse;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.stores.repository.StoreRepository;

@Service
public class DailyClosingService {

    private static final Logger log = LoggerFactory.getLogger(DailyClosingService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final DailyClosingRepository dailyClosingRepository;
    private final DailyClosingCollaboratorRepository dailyClosingCollaboratorRepository;
    private final DailyClosingStoreRepository dailyClosingStoreRepository;
    private final SaleStoreSummaryRepository saleStoreSummaryRepository;
    private final MarketRepository marketRepository;
    private final StoreRepository storeRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final DailyClosingEmailService dailyClosingEmailService;
    private final CollaboratorSalesSummaryService collaboratorSalesSummaryService;
    private final CollaboratorClosingEmailService collaboratorClosingEmailService;
    private final ZoneId businessZone;

    public DailyClosingService(
            DailyClosingRepository dailyClosingRepository,
            DailyClosingCollaboratorRepository dailyClosingCollaboratorRepository,
            DailyClosingStoreRepository dailyClosingStoreRepository,
            SaleStoreSummaryRepository saleStoreSummaryRepository,
            MarketRepository marketRepository,
            StoreRepository storeRepository,
            CurrentTenantProvider currentTenantProvider,
            DailyClosingEmailService dailyClosingEmailService,
            CollaboratorSalesSummaryService collaboratorSalesSummaryService,
            CollaboratorClosingEmailService collaboratorClosingEmailService,
            @Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.dailyClosingRepository = dailyClosingRepository;
        this.dailyClosingCollaboratorRepository = dailyClosingCollaboratorRepository;
        this.dailyClosingStoreRepository = dailyClosingStoreRepository;
        this.saleStoreSummaryRepository = saleStoreSummaryRepository;
        this.marketRepository = marketRepository;
        this.storeRepository = storeRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.dailyClosingEmailService = dailyClosingEmailService;
        this.collaboratorSalesSummaryService = collaboratorSalesSummaryService;
        this.collaboratorClosingEmailService = collaboratorClosingEmailService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional
    public DailyClosingResponse closeDay(Long marketId, LocalDate closingDate, String closedBy) {
        LocalDate effectiveDate = closingDate == null ? LocalDate.now(businessZone) : closingDate;
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        return dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(tenantId, marketId, effectiveDate)
                .map(this::toResponse)
                .orElseGet(() -> createClosing(marketId, effectiveDate, closedBy));
    }

    @Transactional(readOnly = true)
    public DailyClosingResponse getClosing(Long marketId, LocalDate closingDate) {
        LocalDate effectiveDate = closingDate == null ? LocalDate.now(businessZone) : closingDate;
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        DailyClosing closing = dailyClosingRepository.findByMarketIdAndClosingDateWithMarket(tenantId, marketId, effectiveDate)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Todavía no existe un cierre diario para esa tienda en la fecha seleccionada."));
        return toResponse(closing);
    }

    private DailyClosingResponse createClosing(Long marketId, LocalDate closingDate, String closedBy) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Market market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        Instant startAt = closingDate.atStartOfDay(businessZone).toInstant();
        Instant endAt = closingDate.plusDays(1).atStartOfDay(businessZone).toInstant();

        List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries =
                collaboratorSalesSummaryService.summarizeByMarketAndPeriod(marketId, startAt, endAt);

        DailyClosing closing = new DailyClosing();
        closing.setMarket(market);
        closing.setClosingDate(closingDate);
        closing.setSaleCount(collaboratorSummaries.stream().mapToLong(CollaboratorSalesSummaryService.CollaboratorSummary::saleCount).sum());
        closing.setTotalSalesAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalSalesAmount).toList()));
        closing.setTotalCommissionAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalCommissionAmount).toList()));
        closing.setTotalNetAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalNetAmount).toList()));
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(closedBy);
        dailyClosingRepository.save(closing);

        List<DailyClosingCollaborator> collaborators = collaboratorSummaries.stream()
                .map(summary -> toCollaboratorRow(closing, summary))
                .toList();
        if (!collaborators.isEmpty()) {
            dailyClosingCollaboratorRepository.saveAll(collaborators);
        }

        DailyClosingResponse response = toResponse(closing, collaborators);
        try {
            dailyClosingEmailService.sendClosingSummary(market.getEmail(), response);
        } catch (Exception exception) {
            log.warn("Daily closing email could not be sent for market {} and date {}.", marketId, closingDate, exception);
        }
        try {
            collaboratorClosingEmailService.sendDailySummaries(
                    market.getName(),
                    closingDate,
                    collaboratorSummaries);
        } catch (Exception exception) {
            log.warn("Daily collaborator closing emails could not be sent for market {} and date {}.", marketId, closingDate, exception);
        }
        return response;
    }

    private DailyClosingCollaborator toCollaboratorRow(
            DailyClosing closing,
            CollaboratorSalesSummaryService.CollaboratorSummary summary) {
        DailyClosingCollaborator collaborator = new DailyClosingCollaborator();
        collaborator.setDailyClosing(closing);
        collaborator.setCollaboratorUserId(summary.collaboratorUserId());
        collaborator.setCollaboratorNameSnapshot(summary.collaboratorName());
        collaborator.setCollaboratorEmailSnapshot(summary.collaboratorEmail());
        collaborator.setSaleCount(summary.saleCount());
        collaborator.setTotalItems(summary.totalItems());
        collaborator.setTotalSalesAmount(summary.totalSalesAmount());
        collaborator.setTotalCommissionAmount(summary.totalCommissionAmount());
        collaborator.setTotalNetAmount(summary.totalNetAmount());
        return collaborator;
    }

    private DailyClosingResponse toResponse(DailyClosing closing) {
        List<DailyClosingCollaborator> collaborators =
                dailyClosingCollaboratorRepository.findByDailyClosingIdOrderByCollaboratorNameSnapshotAsc(closing.getId());
        if (collaborators.isEmpty()) {
            return rebuildLegacyClosingIfNeeded(closing);
        }
        return toResponse(closing, collaborators);
    }

    private DailyClosingResponse toResponse(DailyClosing closing, List<DailyClosingCollaborator> collaborators) {
        return new DailyClosingResponse(
                closing.getMarket().getId(),
                closing.getMarket().getName(),
                closing.getClosingDate(),
                closing.getSaleCount(),
                closing.getTotalSalesAmount(),
                closing.getTotalCommissionAmount(),
                closing.getTotalNetAmount(),
                closing.getClosedAt(),
                closing.getClosedBy(),
                collaborators.stream()
                        .map(collaborator -> new DailyClosingStoreResponse(
                                collaborator.getCollaboratorUserId(),
                                collaborator.getCollaboratorNameSnapshot(),
                                collaborator.getSaleCount(),
                                collaborator.getTotalSalesAmount(),
                                collaborator.getTotalCommissionAmount(),
                                collaborator.getTotalNetAmount(),
                                collaborator.getTotalItems()))
                        .toList());
    }

    private DailyClosingResponse rebuildLegacyClosingIfNeeded(DailyClosing closing) {
        Instant startAt = closing.getClosingDate().atStartOfDay(businessZone).toInstant();
        Instant endAt = closing.getClosingDate().plusDays(1).atStartOfDay(businessZone).toInstant();
        List<CollaboratorSalesSummaryService.CollaboratorSummary> collaboratorSummaries =
                collaboratorSalesSummaryService.summarizeByMarketAndPeriod(closing.getMarket().getId(), startAt, endAt);
        if (collaboratorSummaries.isEmpty()) {
            List<DailyClosingStore> legacyStores = dailyClosingStoreRepository.findByDailyClosingIdOrderByStoreNameSnapshotAsc(closing.getId());
            return new DailyClosingResponse(
                    closing.getMarket().getId(),
                    closing.getMarket().getName(),
                    closing.getClosingDate(),
                    closing.getSaleCount(),
                    closing.getTotalSalesAmount(),
                    closing.getTotalCommissionAmount(),
                    closing.getTotalNetAmount(),
                    closing.getClosedAt(),
                    closing.getClosedBy(),
                    legacyStores.stream()
                            .map(store -> new DailyClosingStoreResponse(
                                    store.getStore() == null ? null : store.getStore().getId(),
                                    store.getStoreNameSnapshot(),
                                    store.getSaleCount(),
                                    store.getTotalSalesAmount(),
                                    store.getTotalCommissionAmount(),
                                    store.getTotalNetAmount(),
                                    store.getTotalItems()))
                            .toList());
        }

        closing.setSaleCount(collaboratorSummaries.stream().mapToLong(CollaboratorSalesSummaryService.CollaboratorSummary::saleCount).sum());
        closing.setTotalSalesAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalSalesAmount).toList()));
        closing.setTotalCommissionAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalCommissionAmount).toList()));
        closing.setTotalNetAmount(sum(collaboratorSummaries.stream().map(CollaboratorSalesSummaryService.CollaboratorSummary::totalNetAmount).toList()));
        dailyClosingRepository.save(closing);

        List<DailyClosingCollaborator> collaborators = collaboratorSummaries.stream()
                .map(summary -> toCollaboratorRow(closing, summary))
                .toList();
        dailyClosingCollaboratorRepository.saveAll(collaborators);
        return toResponse(closing, collaborators);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(ZERO, BigDecimal::add);
    }
}
