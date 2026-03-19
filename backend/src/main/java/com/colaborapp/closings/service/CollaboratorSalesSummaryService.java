package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.users.domain.User;
import com.colaborapp.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollaboratorSalesSummaryService {

    private static final BigDecimal IVA_DIVISOR = new BigDecimal("1.19");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final SaleItemRepository saleItemRepository;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;

    @Transactional(readOnly = true)
    public List<CollaboratorSummary> summarizeByMarketAndPeriod(Long marketId, Instant startAt, Instant endAt) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        List<SaleItem> items = saleItemRepository.findAllByMarketIdAndPeriodWithDetails(
                tenantId,
                marketId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt);
        return summarize(items);
    }

    @Transactional(readOnly = true)
    public CollaboratorReportData buildCollaboratorReport(Long collaboratorUserId, Instant startAt, Instant endAt) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        List<SaleItem> items = saleItemRepository.findAllByCollaboratorAndPeriodWithDetails(
                tenantId,
                collaboratorUserId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt);
        List<CollaboratorSummary> summaries = summarize(items);
        CollaboratorSummary summary = summaries.isEmpty() ? CollaboratorSummary.empty(collaboratorUserId) : summaries.getFirst();
        List<CollaboratorSaleEntry> entries = items.stream()
                .map(item -> new CollaboratorSaleEntry(
                        item.getSale().getId(),
                        item.getSale().getSaleNumber(),
                        item.getSale().getConfirmedAt(),
                        item.getProductNameSnapshot(),
                        item.getQuantity(),
                        item.getSubtotal(),
                        item.getTotalCommissionAmount(),
                        item.getNetAmount()))
                .toList();
        return new CollaboratorReportData(summary, entries);
    }

    private List<CollaboratorSummary> summarize(List<SaleItem> items) {
        Map<String, MutableCollaboratorSummary> aggregates = new LinkedHashMap<>();
        Map<Long, User> collaboratorsById = loadCollaborators(items);

        for (SaleItem item : items) {
            String key = item.getCollaboratorUserId() != null
                    ? "id:" + item.getCollaboratorUserId()
                    : "name:" + (item.getCollaboratorNameSnapshot() == null ? "sin-colaborador" : item.getCollaboratorNameSnapshot());
            MutableCollaboratorSummary aggregate = aggregates.computeIfAbsent(key, ignored -> {
                User collaborator = item.getCollaboratorUserId() != null ? collaboratorsById.get(item.getCollaboratorUserId()) : null;
                return new MutableCollaboratorSummary(
                        item.getCollaboratorUserId(),
                        item.getCollaboratorNameSnapshot() == null ? "Sin colaborador" : item.getCollaboratorNameSnapshot(),
                        collaborator != null ? collaborator.getEmail() : null,
                        collaborator != null && collaborator.isFactura());
            });
            aggregate.saleIds.add(item.getSale().getId());
            aggregate.totalItems += item.getQuantity();
            aggregate.totalSalesAmount = aggregate.totalSalesAmount.add(item.getSubtotal());
            aggregate.totalCommissionAmount = aggregate.totalCommissionAmount.add(item.getTotalCommissionAmount());
            aggregate.totalNetAmount = aggregate.totalNetAmount.add(item.getNetAmount());
            BigDecimal ivaAmount = calculateIva(item.getSubtotal());
            aggregate.totalIvaAmount = aggregate.totalIvaAmount.add(ivaAmount);
            if (!aggregate.factura) {
                aggregate.ivaToPayAmount = aggregate.ivaToPayAmount.add(ivaAmount);
            }
            aggregate.productSummaries.compute(item.getProductNameSnapshot(), (ignored, current) -> {
                if (current == null) {
                    return new MutableProductSummary(
                            item.getProductNameSnapshot(),
                            item.getProductSkuSnapshot(),
                            item.getQuantity(),
                            item.getSubtotal(),
                            item.getTotalCommissionAmount(),
                            item.getNetAmount());
                }
                current.totalQuantity += item.getQuantity();
                current.totalSalesAmount = current.totalSalesAmount.add(item.getSubtotal());
                current.totalCommissionAmount = current.totalCommissionAmount.add(item.getTotalCommissionAmount());
                current.totalNetAmount = current.totalNetAmount.add(item.getNetAmount());
                return current;
            });
        }

        return aggregates.values().stream()
                .map(MutableCollaboratorSummary::toSummary)
                .toList();
    }

    private Map<Long, User> loadCollaborators(List<SaleItem> items) {
        Set<Long> collaboratorIds = items.stream()
                .map(SaleItem::getCollaboratorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (collaboratorIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(collaboratorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
    }

    private BigDecimal calculateIva(BigDecimal grossAmount) {
        if (grossAmount == null || grossAmount.signum() == 0) {
            return ZERO;
        }
        BigDecimal netAmount = grossAmount.divide(IVA_DIVISOR, 4, RoundingMode.HALF_UP);
        return grossAmount.subtract(netAmount).setScale(4, RoundingMode.HALF_UP);
    }

    public record CollaboratorSummary(
            Long collaboratorUserId,
            String collaboratorName,
            String collaboratorEmail,
            boolean factura,
            long saleCount,
            long totalItems,
            BigDecimal totalSalesAmount,
            BigDecimal totalCommissionAmount,
            BigDecimal totalNetAmount,
            BigDecimal totalIvaAmount,
            BigDecimal ivaToPayAmount,
            List<CollaboratorProductSummary> products) {

        static CollaboratorSummary empty(Long collaboratorUserId) {
            return new CollaboratorSummary(
                    collaboratorUserId,
                    "Sin ventas",
                    null,
                    false,
                    0L,
                    0L,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    List.of());
        }
    }

    public record CollaboratorProductSummary(
            String productName,
            String productSku,
            int totalQuantity,
            BigDecimal totalSalesAmount,
            BigDecimal totalCommissionAmount,
            BigDecimal totalNetAmount) {
    }

    public record CollaboratorSaleEntry(
            Long saleId,
            String saleNumber,
            Instant confirmedAt,
            String productName,
            int quantity,
            BigDecimal totalAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount) {
    }

    public record CollaboratorReportData(
            CollaboratorSummary summary,
            List<CollaboratorSaleEntry> entries) {
    }

    private static final class MutableCollaboratorSummary {
        private final Long collaboratorUserId;
        private final String collaboratorName;
        private final String collaboratorEmail;
        private final boolean factura;
        private final Set<Long> saleIds = new LinkedHashSet<>();
        private long totalItems;
        private BigDecimal totalSalesAmount = ZERO;
        private BigDecimal totalCommissionAmount = ZERO;
        private BigDecimal totalNetAmount = ZERO;
        private BigDecimal totalIvaAmount = ZERO;
        private BigDecimal ivaToPayAmount = ZERO;
        private final Map<String, MutableProductSummary> productSummaries = new LinkedHashMap<>();

        private MutableCollaboratorSummary(Long collaboratorUserId, String collaboratorName, String collaboratorEmail, boolean factura) {
            this.collaboratorUserId = collaboratorUserId;
            this.collaboratorName = collaboratorName;
            this.collaboratorEmail = collaboratorEmail;
            this.factura = factura;
        }

        private CollaboratorSummary toSummary() {
            return new CollaboratorSummary(
                    collaboratorUserId,
                    collaboratorName,
                    collaboratorEmail,
                    factura,
                    saleIds.size(),
                    totalItems,
                    totalSalesAmount,
                    totalCommissionAmount,
                    totalNetAmount,
                    totalIvaAmount,
                    factura ? ZERO : ivaToPayAmount,
                    productSummaries.values().stream()
                            .map(MutableProductSummary::toSummary)
                            .toList());
        }
    }

    private static final class MutableProductSummary {
        private final String productName;
        private final String productSku;
        private int totalQuantity;
        private BigDecimal totalSalesAmount;
        private BigDecimal totalCommissionAmount;
        private BigDecimal totalNetAmount;

        private MutableProductSummary(
                String productName,
                String productSku,
                int totalQuantity,
                BigDecimal totalSalesAmount,
                BigDecimal totalCommissionAmount,
                BigDecimal totalNetAmount) {
            this.productName = productName;
            this.productSku = productSku;
            this.totalQuantity = totalQuantity;
            this.totalSalesAmount = totalSalesAmount;
            this.totalCommissionAmount = totalCommissionAmount;
            this.totalNetAmount = totalNetAmount;
        }

        private CollaboratorProductSummary toSummary() {
            return new CollaboratorProductSummary(
                    productName,
                    productSku,
                    totalQuantity,
                    totalSalesAmount,
                    totalCommissionAmount,
                    totalNetAmount);
        }
    }
}
