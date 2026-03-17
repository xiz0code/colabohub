package com.colaborapp.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.tenant.domain.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id")
    private Market market;

    @Column(nullable = false, unique = true, length = 80)
    private String saleNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDiscountAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissionAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal ufValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal commissionUfValue;

    @Column(precision = 19, scale = 4)
    private BigDecimal commissionPercentageValue;

    @Column(nullable = false)
    private Instant openedAt;

    @Column
    private Instant confirmedAt;
}
