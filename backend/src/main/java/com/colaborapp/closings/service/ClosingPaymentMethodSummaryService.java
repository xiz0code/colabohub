package com.colaborapp.closings.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.closings.web.dto.ClosingPaymentMethodSummaryResponse;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClosingPaymentMethodSummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private final SaleRepository saleRepository;
    private final CurrentTenantProvider currentTenantProvider;

    @Transactional(readOnly = true)
    public List<ClosingPaymentMethodSummaryResponse> summarizeByMarketAndPeriod(Long marketId, Instant startAt, Instant endAt) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Map<PaymentMethod, MutablePaymentSummary> summaries = new LinkedHashMap<>();
        Arrays.stream(PaymentMethod.values())
                .forEach(paymentMethod -> summaries.put(paymentMethod, new MutablePaymentSummary(paymentMethod)));

        for (Object[] row : saleRepository.summarizeMarketPaymentMethodsByPeriod(
                tenantId,
                marketId,
                SaleStatus.CONFIRMED,
                startAt,
                endAt)) {
            PaymentMethod paymentMethod = (PaymentMethod) row[0];
            if (paymentMethod == null) {
                continue;
            }
            MutablePaymentSummary summary = summaries.get(paymentMethod);
            if (summary == null) {
                continue;
            }
            summary.saleCount = ((Number) row[1]).longValue();
            summary.totalSalesAmount = row[2] == null ? ZERO : (BigDecimal) row[2];
        }

        return summaries.values().stream()
                .map(MutablePaymentSummary::toResponse)
                .toList();
    }

    private static final class MutablePaymentSummary {
        private final PaymentMethod paymentMethod;
        private long saleCount;
        private BigDecimal totalSalesAmount = ZERO;

        private MutablePaymentSummary(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        private ClosingPaymentMethodSummaryResponse toResponse() {
            return new ClosingPaymentMethodSummaryResponse(
                    paymentMethod.name(),
                    saleCount,
                    totalSalesAmount);
        }
    }
}
