package com.colaborapp.closings.domain;

import java.math.BigDecimal;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.stores.domain.Store;

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
@Table(name = "daily_closing_stores")
@Getter
@Setter
@NoArgsConstructor
public class DailyClosingStore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_closing_id", nullable = false)
    private DailyClosing dailyClosing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 180)
    private String storeNameSnapshot;

    @Column(nullable = false)
    private Long saleCount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalSalesAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissionAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetAmount;

    @Column(nullable = false)
    private Long totalItems;
}
