package com.colaborapp.sales.service;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.reports.web.dto.CommissionRecalculationResponse;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.sales.domain.UfDailyValue;
import com.colaborapp.sales.repository.SaleItemRepository;
import com.colaborapp.sales.repository.SaleRepository;
import com.colaborapp.sales.repository.SaleStoreSummaryRepository;
import com.colaborapp.sales.repository.UfDailyValueRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.settings.service.UfExternalService;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalesCommissionRecalculationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, HALF_UP);
    private static final BigDecimal UF_TOLERANCE = new BigDecimal("0.01");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleStoreSummaryRepository saleStoreSummaryRepository;
    private final UfDailyValueRepository ufDailyValueRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;
    private final UfExternalService ufExternalService;
    private final PosPricingService posPricingService;
    private final CommissionSettingsService commissionSettingsService;

    @Value("${app.business-zone:America/Santiago}")
    private String businessZoneId;

    @Transactional
    public CommissionRecalculationResponse recalculate(LocalDate dateFrom, LocalDate dateTo) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR);
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException("Selecciona un rango de fechas para recalcular comisiones.");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException("La fecha Hasta no puede ser anterior a Desde.");
        }
        if (dateFrom.plusMonths(2).isBefore(dateTo)) {
            throw new BusinessException("Por seguridad, recalcula como maximo dos meses por operacion.");
        }

        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        ZoneId businessZone = ZoneId.of(businessZoneId);
        Instant startAt = dateFrom.atStartOfDay(businessZone).toInstant();
        Instant endAt = dateTo.plusDays(1).atStartOfDay(businessZone).toInstant();
        Set<Long> allowedMarketIds = Set.copyOf(accessControlService.currentMarketIds());

        List<Sale> visibleSales = saleRepository.findConfirmedByPeriodWithMarket(tenantId, SaleStatus.CONFIRMED, startAt, endAt)
                .stream()
                .filter(sale -> accessControlService.hasRole(RoleCode.ADMIN_SYSTEM)
                        || (sale.getMarket() != null && allowedMarketIds.contains(sale.getMarket().getId())))
                .toList();

        Map<LocalDate, BigDecimal> ufByDate = new HashMap<>();
        int reviewedSales = 0;
        int recalculatedSales = 0;

        for (Sale sale : visibleSales) {
            reviewedSales++;
            if (sale.getConfirmedAt() == null) {
                continue;
            }

            LocalDate saleDate = sale.getConfirmedAt().atZone(businessZone).toLocalDate();
            BigDecimal expectedUfValue = ufByDate.computeIfAbsent(saleDate, date -> fetchAndPersistUfValue(tenantId, date));
            if (ufMatches(sale.getUfValue(), expectedUfValue)) {
                continue;
            }

            sale.setUfValue(expectedUfValue);
            if (sale.getPaymentMethod() != PaymentMethod.DEBITO) {
                saleRepository.save(sale);
                recalculatedSales++;
                continue;
            }

            List<SaleItem> items = saleItemRepository.findAllBySaleIdWithDetails(sale.getId());
            List<SaleStoreSummary> summaries = posPricingService.calculateStoreSummaries(items, sale.getPaymentMethod(), expectedUfValue)
                    .stream()
                    .map(summary -> {
                        summary.setSale(sale);
                        return summary;
                    })
                    .toList();

            sale.setSubtotalAmount(sum(items.stream().map(SaleItem::getLineBaseSubtotal).toList()));
            sale.setTotalDiscountAmount(sum(items.stream().map(SaleItem::getPromotionDiscountAmount).toList()));
            sale.setTotalAmount(sum(items.stream().map(SaleItem::getSubtotal).toList()));
            sale.setTotalCommissionAmount(roundClp(sum(items.stream().map(SaleItem::getTotalCommissionAmount).toList())));
            sale.setTotalNetAmount(sum(items.stream().map(SaleItem::getNetAmount).toList()));

            if (sale.getMarket() != null) {
                var effectiveCommission = commissionSettingsService.getEffectiveCommissionConfig(sale.getMarket().getId());
                sale.setCommissionUfValue(effectiveCommission.commissionUfValue());
                sale.setCommissionPercentageValue(effectiveCommission.commissionPercentageValue());
            }

            saleRepository.save(sale);
            saleItemRepository.saveAll(items);
            persistSummaries(sale, summaries);
            recalculatedSales++;
        }

        return new CommissionRecalculationResponse(dateFrom, dateTo, reviewedSales, recalculatedSales, ufByDate.size());
    }

    private boolean ufMatches(BigDecimal currentUfValue, BigDecimal expectedUfValue) {
        if (currentUfValue == null || expectedUfValue == null) {
            return false;
        }
        return currentUfValue.subtract(expectedUfValue).abs().compareTo(UF_TOLERANCE) <= 0;
    }

    private void persistSummaries(Sale sale, List<SaleStoreSummary> computedSummaries) {
        List<SaleStoreSummary> existingSummaries = saleStoreSummaryRepository.findAllBySaleIdWithStore(sale.getId());
        Map<Long, SaleStoreSummary> existingByStoreId = new HashMap<>();
        for (SaleStoreSummary existing : existingSummaries) {
            existingByStoreId.put(existing.getStore().getId(), existing);
        }

        List<SaleStoreSummary> summariesToPersist = new java.util.ArrayList<>();
        for (SaleStoreSummary computed : computedSummaries) {
            SaleStoreSummary summary = existingByStoreId.remove(computed.getStore().getId());
            if (summary == null) {
                summary = new SaleStoreSummary();
                summary.setSale(sale);
                summary.setStore(computed.getStore());
            }

            summary.setLineCount(computed.getLineCount());
            summary.setUnitCount(computed.getUnitCount());
            summary.setSubtotalAmount(computed.getSubtotalAmount());
            summary.setCommission1Amount(computed.getCommission1Amount());
            summary.setCommission2Amount(computed.getCommission2Amount());
            summary.setCommissionIvaAmount(computed.getCommissionIvaAmount());
            summary.setTotalCommissionAmount(computed.getTotalCommissionAmount());
            summary.setNetAmount(computed.getNetAmount());
            summariesToPersist.add(summary);
        }

        if (!existingByStoreId.isEmpty()) {
            saleStoreSummaryRepository.deleteAll(existingByStoreId.values());
        }
        if (!summariesToPersist.isEmpty()) {
            saleStoreSummaryRepository.saveAll(summariesToPersist);
        }
    }

    private BigDecimal fetchAndPersistUfValue(Long tenantId, LocalDate date) {
        BigDecimal value = ufExternalService.fetchUfValue(date);
        UfDailyValue dailyValue = ufDailyValueRepository.findByTenantIdAndEffectiveDate(tenantId, date).orElseGet(UfDailyValue::new);
        dailyValue.setTenant(currentTenantProvider.getCurrentTenant());
        dailyValue.setEffectiveDate(date);
        dailyValue.setUfValue(value);
        dailyValue.setSource("RECALCULO_COMISIONES");
        ufDailyValueRepository.save(dailyValue);
        return value;
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, HALF_UP);
    }

    private BigDecimal roundClp(BigDecimal value) {
        return value.setScale(0, HALF_UP);
    }
}
