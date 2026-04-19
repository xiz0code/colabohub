package com.colaborapp.reports.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.markets.repository.MarketRepository;
import com.colaborapp.reports.web.dto.SaleDetailStoreSummaryResponse;
import com.colaborapp.reports.web.dto.SaleTodayDetailResponse;
import com.colaborapp.reports.web.dto.CollaboratorSaleEntryResponse;
import com.colaborapp.reports.web.dto.CollaboratorSalesReportResponse;
import com.colaborapp.reports.web.dto.DashboardSummaryResponse;
import com.colaborapp.reports.web.dto.MarketPayoutsTodayResponse;
import com.colaborapp.reports.web.dto.MarketSalesTodayReportResponse;
import com.colaborapp.reports.web.dto.MarketStoreSalesSummaryResponse;
import com.colaborapp.reports.web.dto.SalesTodayDetailsResponse;
import com.colaborapp.reports.web.dto.SalesTodayReportResponse;
import com.colaborapp.reports.web.dto.StoreSaleEntryResponse;
import com.colaborapp.reports.web.dto.StorePayoutSummary;
import com.colaborapp.reports.web.dto.SaleTodayItemResponse;
import com.colaborapp.reports.web.dto.StoreSalesReportResponse;
import com.colaborapp.reports.web.dto.StoreSalesSummaryResponse;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.repository.UserRepository;
import com.colaborapp.stores.repository.StoreRepository;

@Service
public class SalesReportService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleStoreSummaryRepository saleStoreSummaryRepository;
    private final StoreRepository storeRepository;
    private final MarketRepository marketRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;
    private final ZoneId businessZone;

    public SalesReportService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            SaleStoreSummaryRepository saleStoreSummaryRepository,
            StoreRepository storeRepository,
            MarketRepository marketRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CurrentTenantProvider currentTenantProvider,
            AccessControlService accessControlService,
            @Value("${app.business-zone:America/Santiago}") String businessZone) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.saleStoreSummaryRepository = saleStoreSummaryRepository;
        this.storeRepository = storeRepository;
        this.marketRepository = marketRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.accessControlService = accessControlService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional(readOnly = true)
    public SalesTodayReportResponse getTodayReport() {
        ScopedSalesSnapshot snapshot = buildScopedSalesSnapshot();
        return new SalesTodayReportResponse(
                snapshot.businessDate(),
                snapshot.totalAmount(),
                snapshot.totalAmount(),
                snapshot.totalCommission(),
                snapshot.totalNet(),
                snapshot.salesCount(),
                snapshot.storeSummaries());
    }

    @Transactional(readOnly = true)
    public SalesTodayDetailsResponse getTodayDetails() {
        ScopedSalesSnapshot snapshot = buildScopedSalesSnapshot();

        return new SalesTodayDetailsResponse(
                snapshot.businessDate(),
                snapshot.totalAmount(),
                snapshot.totalAmount(),
                snapshot.totalCommission(),
                snapshot.totalNet(),
                snapshot.salesCount(),
                snapshot.storeSummaries(),
                snapshot.sales());
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary() {
        ScopedSalesSnapshot snapshot = buildScopedSalesSnapshot();
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();

        return new DashboardSummaryResponse(
                snapshot.businessDate(),
                snapshot.salesCount(),
                snapshot.totalAmount(),
                snapshot.totalCommission(),
                snapshot.totalNet(),
                countActiveProducts(tenantId),
                countLowStockProducts(tenantId),
                snapshot.storeSummaries());
    }

    @Transactional(readOnly = true)
    public StoreSalesReportResponse getStoreReport(Long storeId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var store = storeRepository.findByIdAndTenantId(storeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda colaboradora solicitada no existe o no tienes acceso a ella."));

        Object[] summary = saleRepository.summarizeStore(tenantId, storeId, SaleStatus.CONFIRMED);
        List<StoreSaleEntryResponse> recentSales = saleRepository
                .findRecentStoreSales(tenantId, storeId, SaleStatus.CONFIRMED, PageRequest.of(0, 10))
                .stream()
                .map(this::toStoreSaleEntry)
                .toList();

        return new StoreSalesReportResponse(
                store.getId(),
                store.getName(),
                toLong(summary[0]),
                toBigDecimal(summary[1]),
                toBigDecimal(summary[2]),
                toBigDecimal(summary[3]),
                recentSales);
    }

    @Transactional(readOnly = true)
    public MarketSalesTodayReportResponse getMarketTodayReport(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        LocalDate businessDate = LocalDate.now(businessZone);
        Instant startAt = businessDate.atStartOfDay(businessZone).toInstant();
        Instant endAt = businessDate.plusDays(1).atStartOfDay(businessZone).toInstant();

        Object[] summary = saleRepository.summarizeMarketByPeriod(tenantId, marketId, SaleStatus.CONFIRMED, startAt, endAt);
        List<MarketStoreSalesSummaryResponse> salesPerStore = saleRepository
                .summarizeMarketStoresByPeriod(tenantId, marketId, SaleStatus.CONFIRMED, startAt, endAt)
                .stream()
                .map(this::toMarketStoreSummary)
                .toList();

        return new MarketSalesTodayReportResponse(
                market.getId(),
                market.getName(),
                businessDate,
                toBigDecimal(summary[0]),
                toLong(summary[1]),
                salesPerStore);
    }

    @Transactional(readOnly = true)
    public MarketPayoutsTodayResponse getMarketPayoutsToday(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        LocalDate businessDate = LocalDate.now(businessZone);
        Instant startAt = businessDate.atStartOfDay(businessZone).toInstant();
        Instant endAt = businessDate.plusDays(1).atStartOfDay(businessZone).toInstant();

        List<StorePayoutSummary> stores = saleRepository
                .summarizeMarketPayoutsByPeriod(tenantId, marketId, SaleStatus.CONFIRMED, startAt, endAt)
                .stream()
                .map(this::toStorePayoutSummary)
                .toList();

        return new MarketPayoutsTodayResponse(
                market.getId(),
                market.getName(),
                businessDate,
                stores);
    }

    @Transactional(readOnly = true)
    public CollaboratorSalesReportResponse getCollaboratorSalesReport(Long collaboratorUserId, LocalDate dateFrom, LocalDate dateTo) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var collaborator = userRepository.findWithAccessById(collaboratorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos al colaborador solicitado."));

        validateCollaboratorAccess(collaboratorUserId, collaborator.getMarkets().stream().map(m -> m.getId()).toList());

        LocalDate effectiveFrom = dateFrom == null ? LocalDate.now(businessZone) : dateFrom;
        LocalDate effectiveTo = dateTo == null ? effectiveFrom : dateTo;
        Instant startAt = effectiveFrom.atStartOfDay(businessZone).toInstant();
        Instant endAt = effectiveTo.plusDays(1).atStartOfDay(businessZone).toInstant();

        List<SaleItem> items = saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                tenantId,
                collaboratorUserId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt);

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(4);
        BigDecimal totalNet = BigDecimal.ZERO.setScale(4);
        BigDecimal totalIva = BigDecimal.ZERO.setScale(4);

        List<CollaboratorSaleEntryResponse> entries = new java.util.ArrayList<>();
        for (SaleItem item : items) {
            totalAmount = totalAmount.add(item.getSubtotal());
            totalCommission = totalCommission.add(item.getTotalCommissionAmount());
            totalNet = totalNet.add(item.getNetAmount());
            totalIva = totalIva.add(item.getSubtotal().subtract(item.getSubtotal().divide(new BigDecimal("1.19"), 4, java.math.RoundingMode.HALF_UP)));
            entries.add(new CollaboratorSaleEntryResponse(
                    item.getSale().getId(),
                    item.getSale().getSaleNumber(),
                    item.getSale().getConfirmedAt(),
                    item.getSale().getPaymentMethod() != null ? item.getSale().getPaymentMethod().name() : null,
                    item.getProductNameSnapshot(),
                    describePromotionLabel(item),
                    item.getQuantity(),
                    item.getBaseUnitPrice(),
                    item.getSale().getUfValue(),
                    item.getCommission1Amount(),
                    item.getCommission2Amount(),
                    item.getCommissionIvaAmount(),
                    item.getSubtotal(),
                    item.getTotalCommissionAmount(),
                    item.getNetAmount()));
        }

        return new CollaboratorSalesReportResponse(
                collaboratorUserId,
                collaborator.getFullName(),
                effectiveFrom,
                effectiveTo,
                totalAmount,
                totalCommission,
                totalNet,
                totalIva,
                entries);
    }

    private StoreSalesSummaryResponse toStoreSummary(Object[] row) {
        return new StoreSalesSummaryResponse(
                (Long) row[0],
                (String) row[1],
                toLong(row[2]),
                toBigDecimal(row[3]),
                toBigDecimal(row[4]),
                toBigDecimal(row[5]));
    }

    private StoreSaleEntryResponse toStoreSaleEntry(Object[] row) {
        return new StoreSaleEntryResponse(
                (Long) row[0],
                (String) row[1],
                (Instant) row[2],
                ((Number) row[3]).intValue(),
                ((Number) row[4]).intValue(),
                toBigDecimal(row[5]),
                toBigDecimal(row[6]),
                toBigDecimal(row[7]));
    }

    private MarketStoreSalesSummaryResponse toMarketStoreSummary(Object[] row) {
        return new MarketStoreSalesSummaryResponse(
                (Long) row[0],
                (String) row[1],
                toBigDecimal(row[2]),
                toLong(row[3]));
    }

    private StorePayoutSummary toStorePayoutSummary(Object[] row) {
        return new StorePayoutSummary(
                (Long) row[0],
                (String) row[1],
                toBigDecimal(row[3]),
                toBigDecimal(row[4]),
                toBigDecimal(row[5]),
                toLong(row[2]));
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4);
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).setScale(4);
        }
        return (BigDecimal) value;
    }

    private DailyRange currentBusinessRange() {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        LocalDate businessDate = LocalDate.now(businessZone);
        Instant startAt = businessDate.atStartOfDay(businessZone).toInstant();
        Instant endAt = businessDate.plusDays(1).atStartOfDay(businessZone).toInstant();
        return new DailyRange(tenantId, businessDate, startAt, endAt);
    }

    private ScopedSalesSnapshot buildScopedSalesSnapshot() {
        DailyRange range = currentBusinessRange();
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            return buildStoreUserSnapshot(range, accessControlService.getCurrentUser().user().getId());
        }

        List<SaleStoreSummary> visibleSummaries = filterVisibleSummaries(
                saleStoreSummaryRepository.findByPeriodWithSaleAndStore(
                        range.tenantId(),
                        SaleStatus.CONFIRMED,
                        range.startAt(),
                        range.endAt()));

        Map<Long, SaleAggregate> sales = new LinkedHashMap<>();
        Map<Long, StoreAggregate> stores = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(4);
        BigDecimal totalNet = BigDecimal.ZERO.setScale(4);

        for (SaleStoreSummary summary : visibleSummaries) {
            totalAmount = totalAmount.add(summary.getSubtotalAmount());
            totalCommission = totalCommission.add(summary.getTotalCommissionAmount());
            totalNet = totalNet.add(summary.getNetAmount());

            sales.computeIfAbsent(summary.getSale().getId(), ignored -> new SaleAggregate(
                    summary.getSale().getId(),
                    summary.getSale().getSaleNumber(),
                    summary.getSale().getConfirmedAt()))
                    .add(summary);

            stores.computeIfAbsent(summary.getStore().getMarket().getId(), ignored -> new StoreAggregate(
                    summary.getStore().getMarket().getId(),
                    summary.getStore().getMarket().getName()))
                    .add(summary);
        }

        Map<Long, List<SaleItem>> itemsBySaleId = saleItemRepository.findAllBySaleIdInWithDetails(sales.keySet().stream().toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getSale().getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));

        sales.values().forEach(saleAggregate -> saleAggregate.attachItems(itemsBySaleId.getOrDefault(saleAggregate.saleId(), List.of())));

        return new ScopedSalesSnapshot(
                range.businessDate(),
                sales.size(),
                totalAmount,
                totalCommission,
                totalNet,
                stores.values().stream().map(StoreAggregate::toResponse).toList(),
                sales.values().stream().map(SaleAggregate::toResponse).toList());
    }

    private ScopedSalesSnapshot buildStoreUserSnapshot(DailyRange range, Long collaboratorUserId) {
        List<SaleItem> items = saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                range.tenantId(),
                collaboratorUserId,
                SaleStatus.CONFIRMED,
                range.startAt(),
                range.endAt());

        Map<Long, SaleAggregate> sales = new LinkedHashMap<>();
        Map<Long, StoreAggregate> spaces = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        BigDecimal totalCommission = BigDecimal.ZERO.setScale(4);
        BigDecimal totalNet = BigDecimal.ZERO.setScale(4);

        for (SaleItem item : items) {
            totalAmount = totalAmount.add(item.getSubtotal());
            totalCommission = totalCommission.add(item.getTotalCommissionAmount());
            totalNet = totalNet.add(item.getNetAmount());

            sales.computeIfAbsent(item.getSale().getId(), ignored -> new SaleAggregate(
                    item.getSale().getId(),
                    item.getSale().getSaleNumber(),
                    item.getSale().getConfirmedAt()))
                    .addItem(item);

            Long marketId = item.getStore().getMarket().getId();
            String marketName = item.getStore().getMarket().getName();
            spaces.computeIfAbsent(marketId, ignored -> new StoreAggregate(marketId, marketName))
                    .addItem(item);
        }

        return new ScopedSalesSnapshot(
                range.businessDate(),
                sales.size(),
                totalAmount,
                totalCommission,
                totalNet,
                spaces.values().stream().map(StoreAggregate::toResponse).toList(),
                sales.values().stream().map(SaleAggregate::toResponse).toList());
    }

    private List<SaleStoreSummary> filterVisibleSummaries(List<SaleStoreSummary> summaries) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return summaries;
        }

        Set<Long> allowedStoreIds = Set.copyOf(accessControlService.currentStoreIds());
        Set<Long> allowedMarketIds = Set.copyOf(accessControlService.currentMarketIds());

        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            return summaries.stream()
                    .filter(summary -> allowedStoreIds.contains(summary.getStore().getId()))
                    .toList();
        }

        return summaries.stream()
                .filter(summary -> allowedMarketIds.contains(summary.getStore().getMarket().getId()))
                .toList();
    }

    private long countActiveProducts(Long tenantId) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return productRepository.countByTenantIdAndStatus(tenantId, ProductStatus.ACTIVE);
        }

        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long ownerUserId = accessControlService.getCurrentUser().user().getId();
            if (ownerUserId == null) {
                return 0L;
            }
            return productRepository.countByTenantIdAndOwnerUser_IdAndStatus(tenantId, ownerUserId, ProductStatus.ACTIVE);
        }

        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            return 0L;
        }
        return productRepository.countByTenantIdAndStore_Market_IdInAndStatus(tenantId, marketIds, ProductStatus.ACTIVE);
    }

    private void validateCollaboratorAccess(Long collaboratorUserId, List<Long> collaboratorMarketIds) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return;
        }
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            if (!currentUserId.equals(collaboratorUserId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para realizar esta accion.");
            }
            return;
        }

        Set<Long> allowedMarketIds = Set.copyOf(accessControlService.currentMarketIds());
        boolean allowed = collaboratorMarketIds.stream().anyMatch(allowedMarketIds::contains);
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para realizar esta accion.");
        }
    }

    private String describePromotionLabel(SaleItem item) {
        if (item.getAppliedPromotionName() != null && !item.getAppliedPromotionName().isBlank()) {
            return item.getAppliedPromotionName();
        }
        return switch (item.getPricingType()) {
            case PROMOTION -> "Promocion aplicada";
            case NORMAL -> "Sin promocion";
        };
    }

    private long countLowStockProducts(Long tenantId) {
        if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
            return productRepository.countByTenantIdAndStatusAndStockLessThanEqual(tenantId, ProductStatus.ACTIVE, LOW_STOCK_THRESHOLD);
        }

        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long ownerUserId = accessControlService.getCurrentUser().user().getId();
            if (ownerUserId == null) {
                return 0L;
            }
            return productRepository.countByTenantIdAndOwnerUser_IdAndStatusAndStockLessThanEqual(
                    tenantId,
                    ownerUserId,
                    ProductStatus.ACTIVE,
                    LOW_STOCK_THRESHOLD);
        }

        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            return 0L;
        }
        return productRepository.countByTenantIdAndStore_Market_IdInAndStatusAndStockLessThanEqual(
                tenantId,
                marketIds,
                ProductStatus.ACTIVE,
                LOW_STOCK_THRESHOLD);
    }

    private static final class SaleAggregate {
        private final Long saleId;
        private final String saleNumber;
        private final Instant confirmedAt;
        private BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalCommission = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalNet = BigDecimal.ZERO.setScale(4);
        private final List<SaleDetailStoreSummaryResponse> stores = new java.util.ArrayList<>();
        private final List<SaleTodayItemResponse> items = new java.util.ArrayList<>();

        private SaleAggregate(Long saleId, String saleNumber, Instant confirmedAt) {
            this.saleId = saleId;
            this.saleNumber = saleNumber;
            this.confirmedAt = confirmedAt;
        }

        private Long saleId() {
            return saleId;
        }

        private void add(SaleStoreSummary summary) {
            totalAmount = totalAmount.add(summary.getSubtotalAmount());
            totalCommission = totalCommission.add(summary.getTotalCommissionAmount());
            totalNet = totalNet.add(summary.getNetAmount());
            stores.add(new SaleDetailStoreSummaryResponse(
                    summary.getStore().getMarket().getId(),
                    summary.getStore().getMarket().getName(),
                    summary.getLineCount(),
                    summary.getUnitCount(),
                    summary.getSubtotalAmount(),
                    summary.getTotalCommissionAmount(),
                    summary.getNetAmount()));
        }

        private void addItem(SaleItem item) {
            totalAmount = totalAmount.add(item.getSubtotal());
            totalCommission = totalCommission.add(item.getTotalCommissionAmount());
            totalNet = totalNet.add(item.getNetAmount());
            items.add(new SaleTodayItemResponse(
                    item.getId(),
                    item.getProductNameSnapshot(),
                    item.getCollaboratorNameSnapshot(),
                    item.getStore().getMarket().getName(),
                    item.getQuantity(),
                    item.getSubtotal(),
                    item.getTotalCommissionAmount(),
                    item.getNetAmount()));
        }

        private void attachItems(List<SaleItem> saleItems) {
            for (SaleItem item : saleItems) {
                items.add(new SaleTodayItemResponse(
                    item.getId(),
                    item.getProductNameSnapshot(),
                    item.getCollaboratorNameSnapshot(),
                    item.getStore().getMarket().getName(),
                    item.getQuantity(),
                    item.getSubtotal(),
                        item.getTotalCommissionAmount(),
                        item.getNetAmount()));
            }
        }

        private SaleTodayDetailResponse toResponse() {
            return new SaleTodayDetailResponse(
                    saleId,
                    saleNumber,
                    confirmedAt,
                    totalAmount,
                    totalCommission,
                    totalNet,
                    stores,
                    items);
        }
    }

    private static final class StoreAggregate {
        private final Long storeId;
        private final String storeName;
        private final Set<Long> saleIds = new java.util.LinkedHashSet<>();
        private BigDecimal subtotalAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalCommissionAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal netAmount = BigDecimal.ZERO.setScale(4);

        private StoreAggregate(Long storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }

        private void add(SaleStoreSummary summary) {
            saleIds.add(summary.getSale().getId());
            subtotalAmount = subtotalAmount.add(summary.getSubtotalAmount());
            totalCommissionAmount = totalCommissionAmount.add(summary.getTotalCommissionAmount());
            netAmount = netAmount.add(summary.getNetAmount());
        }

        private void addItem(SaleItem item) {
            saleIds.add(item.getSale().getId());
            subtotalAmount = subtotalAmount.add(item.getSubtotal());
            totalCommissionAmount = totalCommissionAmount.add(item.getTotalCommissionAmount());
            netAmount = netAmount.add(item.getNetAmount());
        }

        private StoreSalesSummaryResponse toResponse() {
            return new StoreSalesSummaryResponse(
                    storeId,
                    storeName,
                    saleIds.size(),
                    subtotalAmount,
                    totalCommissionAmount,
                    netAmount);
        }
    }

    private record DailyRange(
            Long tenantId,
            LocalDate businessDate,
            Instant startAt,
            Instant endAt) {
    }

    private record ScopedSalesSnapshot(
            LocalDate businessDate,
            long salesCount,
            BigDecimal totalAmount,
            BigDecimal totalCommission,
            BigDecimal totalNet,
            List<StoreSalesSummaryResponse> storeSummaries,
            List<SaleTodayDetailResponse> sales) {
    }
}
