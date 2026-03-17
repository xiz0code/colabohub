package com.colaborapp.closings.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.markets.domain.Market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "daily_closings")
@Getter
@Setter
@NoArgsConstructor
public class DailyClosing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @Column(nullable = false)
    private LocalDate closingDate;

    @Column(nullable = false)
    private Long saleCount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalSalesAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissionAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetAmount;

    @Column(nullable = false)
    private Instant closedAt;

    @Column(nullable = false, length = 120)
    private String closedBy;
}
