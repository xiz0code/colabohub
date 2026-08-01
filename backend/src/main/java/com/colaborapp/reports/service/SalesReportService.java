package com.colaborapp.reports.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
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
import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.repository.PickupRepository;
import com.colaborapp.reports.web.dto.SaleDetailStoreSummaryResponse;
import com.colaborapp.reports.web.dto.SaleTodayDetailResponse;
import com.colaborapp.reports.web.dto.CollaboratorSaleEntryResponse;
import com.colaborapp.reports.web.dto.CollaboratorSalesReportResponse;
import com.colaborapp.reports.web.dto.DashboardSummaryResponse;
import com.colaborapp.reports.web.dto.DashboardPaymentMethodResponse;
import com.colaborapp.reports.web.dto.DashboardTrendPointResponse;
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
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.users.domain.User;
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
    private final PickupRepository pickupRepository;
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
            PickupRepository pickupRepository,
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
        this.pickupRepository = pickupRepository;
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
        LocalDate businessDate = snapshot.businessDate();
        Instant trendStartAt = businessDate.minusDays(6).atStartOfDay(businessZone).toInstant();
        Instant trendEndAt = businessDate.plusDays(1).atStartOfDay(businessZone).toInstant();
        List<SaleItem> trendItems = loadScopedItems(tenantId, trendStartAt, trendEndAt);
        List<DashboardTrendPointResponse> trend = buildDashboardTrend(businessDate, trendItems);
        DashboardTrendPointResponse previousDay = trend.stream()
                .filter(point -> point.date().equals(businessDate.minusDays(1)))
                .findFirst()
                .orElse(new DashboardTrendPointResponse(businessDate.minusDays(1), 0L, BigDecimal.ZERO, BigDecimal.ZERO));

        return new DashboardSummaryResponse(
                businessDate,
                snapshot.salesCount(),
                snapshot.totalAmount(),
                snapshot.totalCommission(),
                snapshot.totalNet(),
                countActiveProducts(tenantId),
                countLowStockProducts(tenantId),
                countPendingPickups(tenantId),
                previousDay.salesCount(),
                previousDay.totalAmount(),
                calculateChangePercentage(snapshot.totalAmount(), previousDay.totalAmount()),
                trend,
                buildPaymentMethodSummary(trendItems, businessDate),
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
        Set<Long> activeCollaboratorIds = resolveActiveStoreUserIds();
        List<SaleItem> visibleItems = filterVisibleItems(
                saleItemRepository.findAllByMarketIdAndPeriodWithDetails(
                        tenantId,
                        marketId,
                        SaleStatus.CONFIRMED,
                        startAt,
                        endAt),
                activeCollaboratorIds);

        Map<Long, MarketStoreAggregate> salesPerStoreMap = new LinkedHashMap<>();
        BigDecimal totalSales = BigDecimal.ZERO.setScale(4);
        long totalItems = 0L;

        for (SaleItem item : visibleItems) {
            totalSales = totalSales.add(item.getSubtotal());
            totalItems += item.getQuantity();

            Long storeKey = resolveReportStoreId(item);
            String storeName = resolveReportStoreName(item);
            salesPerStoreMap.computeIfAbsent(storeKey, ignored -> new MarketStoreAggregate(storeKey, storeName))
                    .add(item);
        }

        return new MarketSalesTodayReportResponse(
                market.getId(),
                market.getName(),
                businessDate,
                totalSales,
                totalItems,
                salesPerStoreMap.values().stream().map(MarketStoreAggregate::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public MarketPayoutsTodayResponse getMarketPayoutsToday(Long marketId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var market = marketRepository.findByIdAndTenantId(marketId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("La tienda solicitada no existe o no tienes acceso a ella."));

        LocalDate businessDate = LocalDate.now(businessZone);
        Instant startAt = businessDate.atStartOfDay(businessZone).toInstant();
        Instant endAt = businessDate.plusDays(1).atStartOfDay(businessZone).toInstant();
        Set<Long> activeCollaboratorIds = resolveActiveStoreUserIds();
        List<SaleItem> visibleItems = filterVisibleItems(
                saleItemRepository.findAllByMarketIdAndPeriodWithDetails(
                        tenantId,
                        marketId,
                        SaleStatus.CONFIRMED,
                        startAt,
                        endAt),
                activeCollaboratorIds);

        Map<Long, MarketPayoutAggregate> storesMap = new LinkedHashMap<>();
        for (SaleItem item : visibleItems) {
            Long storeKey = resolveReportStoreId(item);
            String storeName = resolveReportStoreName(item);
            storesMap.computeIfAbsent(storeKey, ignored -> new MarketPayoutAggregate(storeKey, storeName))
                    .add(item);
        }

        return new MarketPayoutsTodayResponse(
                market.getId(),
                market.getName(),
                businessDate,
                storesMap.values().stream().map(MarketPayoutAggregate::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public CollaboratorSalesReportResponse getCollaboratorSalesReport(Long collaboratorUserId, LocalDate dateFrom, LocalDate dateTo) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var collaborator = userRepository.findWithAccessById(collaboratorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos al colaborador solicitado."));
        if (!collaborator.isActive() || !hasRole(collaborator, RoleCode.STORE_USER)) {
            throw new ResourceNotFoundException("No encontramos al colaborador solicitado.");
        }

        validateCollaboratorAccess(collaboratorUserId, collaborator.getMarkets().stream().map(m -> m.getId()).toList());

        LocalDate effectiveFrom = dateFrom == null ? LocalDate.now(businessZone) : dateFrom;
        LocalDate effectiveTo = dateTo == null ? effectiveFrom : dateTo;
        Instant startAt = effectiveFrom.atStartOfDay(businessZone).toInstant();
        Instant endAt = effectiveTo.plusDays(1).atStartOfDay(businessZone).toInstant();

        List<SaleItem> items = filterVisibleItems(
                saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                        tenantId,
                        collaboratorUserId,
                        SaleStatus.CONFIRMED,
                        startAt,
                        endAt),
                resolveActiveStoreUserIds());

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
        Set<Long> activeCollaboratorIds = resolveActiveStoreUserIds();
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            return buildStoreUserSnapshot(range, accessControlService.getCurrentUser().user().getId(), activeCollaboratorIds);
        }

        List<SaleItem> visibleItems = filterVisibleItems(
                saleItemRepository.findAllByTenantAndPeriodWithDetails(
                        range.tenantId(),
                        SaleStatus.CONFIRMED,
                        range.startAt(),
                        range.endAt()),
                activeCollaboratorIds);

        return buildSnapshotFromItems(range.businessDate(), visibleItems);
    }

    private ScopedSalesSnapshot buildStoreUserSnapshot(DailyRange range, Long collaboratorUserId, Set<Long> activeCollaboratorIds) {
        List<SaleItem> visibleItems = filterVisibleItems(
                saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                        range.tenantId(),
                        collaboratorUserId,
                        SaleStatus.CONFIRMED,
                        range.startAt(),
                        range.endAt()),
                activeCollaboratorIds);

        return buildSnapshotFromItems(range.businessDate(), visibleItems);
    }

    private List<SaleItem> loadScopedItems(Long tenantId, Instant startAt, Instant endAt) {
        Set<Long> activeCollaboratorIds = resolveActiveStoreUserIds();
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long collaboratorUserId = accessControlService.getCurrentUser().user().getId();
            return filterVisibleItems(
                    saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                            tenantId,
                            collaboratorUserId,
                            SaleStatus.CONFIRMED,
                            startAt,
                            endAt),
                    activeCollaboratorIds);
        }

        return filterVisibleItems(
                saleItemRepository.findAllByTenantAndPeriodWithDetails(
                        tenantId,
                        SaleStatus.CONFIRMED,
                        startAt,
                        endAt),
                activeCollaboratorIds);
    }

    private List<DashboardTrendPointResponse> buildDashboardTrend(LocalDate businessDate, List<SaleItem> items) {
        Map<LocalDate, DashboardDayAggregate> days = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = businessDate.minusDays(offset);
            days.put(date, new DashboardDayAggregate(date));
        }

        for (SaleItem item : items) {
            if (item.getSale().getConfirmedAt() == null) {
                continue;
            }
            LocalDate date = item.getSale().getConfirmedAt().atZone(businessZone).toLocalDate();
            DashboardDayAggregate day = days.get(date);
            if (day != null) {
                day.add(item);
            }
        }

        return days.values().stream().map(DashboardDayAggregate::toResponse).toList();
    }

    private List<DashboardPaymentMethodResponse> buildPaymentMethodSummary(List<SaleItem> items, LocalDate businessDate) {
        Map<String, DashboardPaymentAggregate> summaries = new LinkedHashMap<>();
        for (SaleItem item : items) {
            if (item.getSale().getConfirmedAt() == null
                    || !item.getSale().getConfirmedAt().atZone(businessZone).toLocalDate().equals(businessDate)) {
                continue;
            }
            String paymentMethod = item.getSale().getPaymentMethod() == null
                    ? "UNKNOWN"
                    : item.getSale().getPaymentMethod().name();
            summaries.computeIfAbsent(paymentMethod, DashboardPaymentAggregate::new).add(item);
        }
        return summaries.values().stream()
                .sorted((left, right) -> right.totalAmount.compareTo(left.totalAmount))
                .map(DashboardPaymentAggregate::toResponse)
                .toList();
    }

    private BigDecimal calculateChangePercentage(BigDecimal currentAmount, BigDecimal previousAmount) {
        if (previousAmount == null || previousAmount.compareTo(BigDecimal.ZERO) == 0) {
            return currentAmount != null && currentAmount.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("100.00")
                    : BigDecimal.ZERO.setScale(2);
        }
        return currentAmount.subtract(previousAmount)
                .multiply(new BigDecimal("100"))
                .divide(previousAmount, 2, RoundingMode.HALF_UP);
    }

    private long countPendingPickups(Long tenantId) {
        boolean isSystemAdmin = accessControlService.hasRole(RoleCode.ADMIN_SYSTEM);
        boolean isStoreUser = accessControlService.hasRole(RoleCode.STORE_USER);
        List<Long> marketIds = isSystemAdmin ? List.of() : accessControlService.currentMarketIds();
        Long collaboratorUserId = isStoreUser ? accessControlService.getCurrentUser().user().getId() : null;
        return pickupRepository.countDashboardPending(
                tenantId,
                marketIds,
                marketIds.isEmpty(),
                collaboratorUserId,
                List.of(PickupStatus.PENDING, PickupStatus.CHECKOUT_IN_PROGRESS));
    }

    private ScopedSalesSnapshot buildSnapshotFromItems(LocalDate businessDate, List<SaleItem> items) {
        Map<Long, SaleAggregate> sales = new LinkedHashMap<>();
        Map<Long, StoreAggregate> stores = new LinkedHashMap<>();
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
            stores.computeIfAbsent(marketId, ignored -> new StoreAggregate(marketId, marketName))
                    .addItem(item);
        }

        return new ScopedSalesSnapshot(
                businessDate,
                sales.size(),
                totalAmount,
                totalCommission,
                totalNet,
                stores.values().stream().map(StoreAggregate::toResponse).toList(),
                sales.values().stream().map(SaleAggregate::toResponse).toList());
    }

    private List<SaleItem> filterVisibleItems(List<SaleItem> items, Set<Long> activeCollaboratorIds) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<Long> allowedStoreIds = accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)
                ? Set.of()
                : Set.copyOf(accessControlService.currentStoreIds());
        Set<Long> allowedMarketIds = accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)
                ? Set.of()
                : Set.copyOf(accessControlService.currentMarketIds());
        Long currentStoreUserId = accessControlService.hasRole(RoleCode.STORE_USER)
                ? accessControlService.getCurrentUser().user().getId()
                : null;

        return items.stream()
                .filter(this::isActiveStoreItem)
                .filter(item -> item.getCollaboratorUserId() == null || activeCollaboratorIds.contains(item.getCollaboratorUserId()))
                .filter(item -> {
                    if (accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)) {
                        return true;
                    }
                    if (accessControlService.hasRole(RoleCode.STORE_USER)) {
                        return currentStoreUserId != null
                                && currentStoreUserId.equals(item.getCollaboratorUserId())
                                && (allowedStoreIds.isEmpty() || allowedStoreIds.contains(item.getStore().getId()));
                    }
                    return allowedMarketIds.contains(item.getStore().getMarket().getId());
                })
                .toList();
    }

    private boolean isActiveStoreItem(SaleItem item) {
        return item.getStore() != null
                && item.getStore().getStatus() == StoreStatus.ACTIVE
                && item.getStore().getMarket() != null;
    }

    private Set<Long> resolveActiveStoreUserIds() {
        List<User> users = userRepository.findAllByOrderByFullNameAsc();
        if (users == null || users.isEmpty()) {
            return Set.of();
        }
        return users.stream()
                .filter(User::isActive)
                .filter(user -> hasRole(user, RoleCode.STORE_USER))
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return user.getRoles().stream().anyMatch(role -> role.getCode() == roleCode);
    }

    private Long resolveReportStoreId(SaleItem item) {
        return item.getCollaboratorUserId() != null ? item.getCollaboratorUserId() : item.getStore().getId();
    }

    private String resolveReportStoreName(SaleItem item) {
        if (item.getCollaboratorNameSnapshot() != null && !item.getCollaboratorNameSnapshot().isBlank()) {
            return item.getCollaboratorNameSnapshot();
        }
        return item.getStore().getName();
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

    private static final class DashboardDayAggregate {
        private final LocalDate date;
        private final Set<Long> saleIds = new HashSet<>();
        private BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalNet = BigDecimal.ZERO.setScale(4);

        private DashboardDayAggregate(LocalDate date) {
            this.date = date;
        }

        private void add(SaleItem item) {
            saleIds.add(item.getSale().getId());
            totalAmount = totalAmount.add(item.getSubtotal());
            totalNet = totalNet.add(item.getNetAmount());
        }

        private DashboardTrendPointResponse toResponse() {
            return new DashboardTrendPointResponse(date, saleIds.size(), totalAmount, totalNet);
        }
    }

    private static final class DashboardPaymentAggregate {
        private final String paymentMethod;
        private final Set<Long> saleIds = new HashSet<>();
        private BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);

        private DashboardPaymentAggregate(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        private void add(SaleItem item) {
            saleIds.add(item.getSale().getId());
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        private DashboardPaymentMethodResponse toResponse() {
            return new DashboardPaymentMethodResponse(paymentMethod, saleIds.size(), totalAmount);
        }
    }

    private static final class SaleAggregate {
        private final Long saleId;
        private final String saleNumber;
        private final Instant confirmedAt;
        private BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalCommission = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalNet = BigDecimal.ZERO.setScale(4);
        private final Map<Long, SaleStoreLineAggregate> stores = new LinkedHashMap<>();
        private final List<SaleTodayItemResponse> items = new java.util.ArrayList<>();

        private SaleAggregate(Long saleId, String saleNumber, Instant confirmedAt) {
            this.saleId = saleId;
            this.saleNumber = saleNumber;
            this.confirmedAt = confirmedAt;
        }

        private void addItem(SaleItem item) {
            totalAmount = totalAmount.add(item.getSubtotal());
            totalCommission = totalCommission.add(item.getTotalCommissionAmount());
            totalNet = totalNet.add(item.getNetAmount());
            addStoreContribution(item);
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

        private void addStoreContribution(SaleItem item) {
            Long marketId = item.getStore().getMarket().getId();
            String marketName = item.getStore().getMarket().getName();
            stores.computeIfAbsent(marketId, ignored -> new SaleStoreLineAggregate(marketId, marketName))
                    .add(item);
        }

        private SaleTodayDetailResponse toResponse() {
            return new SaleTodayDetailResponse(
                    saleId,
                    saleNumber,
                    confirmedAt,
                    totalAmount,
                    totalCommission,
                    totalNet,
                    stores.values().stream().map(SaleStoreLineAggregate::toResponse).toList(),
                    items);
        }
    }

    private static final class SaleStoreLineAggregate {
        private final Long storeId;
        private final String storeName;
        private int lineCount = 0;
        private int unitCount = 0;
        private BigDecimal subtotalAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal totalCommissionAmount = BigDecimal.ZERO.setScale(4);
        private BigDecimal netAmount = BigDecimal.ZERO.setScale(4);

        private SaleStoreLineAggregate(Long storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }

        private void add(SaleItem item) {
            lineCount += 1;
            unitCount += item.getQuantity();
            subtotalAmount = subtotalAmount.add(item.getSubtotal());
            totalCommissionAmount = totalCommissionAmount.add(item.getTotalCommissionAmount());
            netAmount = netAmount.add(item.getNetAmount());
        }

        private SaleDetailStoreSummaryResponse toResponse() {
            return new SaleDetailStoreSummaryResponse(
                    storeId,
                    storeName,
                    lineCount,
                    unitCount,
                    subtotalAmount,
                    totalCommissionAmount,
                    netAmount);
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

    private static final class MarketStoreAggregate {
        private final Long storeId;
        private final String storeName;
        private BigDecimal totalSales = BigDecimal.ZERO.setScale(4);
        private long totalItems = 0L;

        private MarketStoreAggregate(Long storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }

        private void add(SaleItem item) {
            totalSales = totalSales.add(item.getSubtotal());
            totalItems += item.getQuantity();
        }

        private MarketStoreSalesSummaryResponse toResponse() {
            return new MarketStoreSalesSummaryResponse(storeId, storeName, totalSales, totalItems);
        }
    }

    private static final class MarketPayoutAggregate {
        private final Long storeId;
        private final String storeName;
        private final Set<Long> saleIds = new java.util.LinkedHashSet<>();
        private BigDecimal subtotal = BigDecimal.ZERO.setScale(4);
        private BigDecimal commission = BigDecimal.ZERO.setScale(4);
        private BigDecimal netAmount = BigDecimal.ZERO.setScale(4);

        private MarketPayoutAggregate(Long storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }

        private void add(SaleItem item) {
            saleIds.add(item.getSale().getId());
            subtotal = subtotal.add(item.getSubtotal());
            commission = commission.add(item.getTotalCommissionAmount());
            netAmount = netAmount.add(item.getNetAmount());
        }

        private StorePayoutSummary toResponse() {
            return new StorePayoutSummary(storeId, storeName, subtotal, commission, netAmount, saleIds.size());
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
