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
import com.colaborapp.closings.domain.DailyClosingStore;
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
    private final DailyClosingStoreRepository dailyClosingStoreRepository;
    private final SaleStoreSummaryRepository saleStoreSummaryRepository;
    private final MarketRepository marketRepository;
    private final StoreRepository storeRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final DailyClosingEmailService dailyClosingEmailService;
    private final ZoneId businessZone;

    public DailyClosingService(
            DailyClosingRepository dailyClosingRepository,
            DailyClosingStoreRepository dailyClosingStoreRepository,
            SaleStoreSummaryRepository saleStoreSummaryRepository,
            MarketRepository marketRepository,
            StoreRepository storeRepository,
            CurrentTenantProvider currentTenantProvider,
            DailyClosingEmailService dailyClosingEmailService,
            @Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.dailyClosingRepository = dailyClosingRepository;
        this.dailyClosingStoreRepository = dailyClosingStoreRepository;
        this.saleStoreSummaryRepository = saleStoreSummaryRepository;
        this.marketRepository = marketRepository;
        this.storeRepository = storeRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.dailyClosingEmailService = dailyClosingEmailService;
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

        Object[] totals = saleStoreSummaryRepository.summarizeClosingTotals(
                tenantId,
                marketId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt);
        List<Object[]> storeRows = saleStoreSummaryRepository.summarizeClosingStores(
                tenantId,
                marketId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt);

        DailyClosing closing = new DailyClosing();
        closing.setMarket(market);
        closing.setClosingDate(closingDate);
        closing.setSaleCount(toLong(totals[0]));
        closing.setTotalSalesAmount(toBigDecimal(totals[1]));
        closing.setTotalCommissionAmount(toBigDecimal(totals[2]));
        closing.setTotalNetAmount(toBigDecimal(totals[3]));
        closing.setClosedAt(Instant.now());
        closing.setClosedBy(closedBy);
        dailyClosingRepository.save(closing);

        List<DailyClosingStore> stores = storeRows.stream()
                .map(row -> toClosingStore(closing, row))
                .toList();
        if (!stores.isEmpty()) {
            dailyClosingStoreRepository.saveAll(stores);
        }

        DailyClosingResponse response = toResponse(closing, stores);
        try {
            dailyClosingEmailService.sendClosingSummary(market.getEmail(), response);
        } catch (Exception exception) {
            log.warn("Daily closing email could not be sent for market {} and date {}.", marketId, closingDate, exception);
        }
        return response;
    }

    private DailyClosingStore toClosingStore(DailyClosing closing, Object[] row) {
        Object[] values = unwrapRow(row);
        DailyClosingStore store = new DailyClosingStore();
        store.setDailyClosing(closing);
        store.setStore(storeRepository.getReferenceById(toLong(values[0])));
        store.setStoreNameSnapshot((String) values[1]);
        store.setSaleCount(toLong(values[2]));
        store.setTotalSalesAmount(toBigDecimal(values[3]));
        store.setTotalCommissionAmount(toBigDecimal(values[4]));
        store.setTotalNetAmount(toBigDecimal(values[5]));
        store.setTotalItems(toLong(values[6]));
        return store;
    }

    private DailyClosingResponse toResponse(DailyClosing closing) {
        List<DailyClosingStore> stores = dailyClosingStoreRepository.findByDailyClosingIdOrderByStoreNameSnapshotAsc(closing.getId());
        return toResponse(closing, stores);
    }

    private DailyClosingResponse toResponse(DailyClosing closing, List<DailyClosingStore> stores) {
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
                stores.stream()
                        .map(store -> new DailyClosingStoreResponse(
                                store.getStore().getId(),
                                store.getStoreNameSnapshot(),
                                store.getSaleCount(),
                                store.getTotalSalesAmount(),
                                store.getTotalCommissionAmount(),
                                store.getTotalNetAmount(),
                                store.getTotalItems()))
                        .toList());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).setScale(4);
        }
        return (BigDecimal) value;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Object[] unwrapRow(Object[] row) {
        return row.length == 1 && row[0] instanceof Object[] nested ? nested : row;
    }
}
