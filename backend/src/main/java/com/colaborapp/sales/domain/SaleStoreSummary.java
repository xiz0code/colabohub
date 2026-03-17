package com.colaborapp.sales.domain;

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
@Table(name = "sale_store_summaries")
@Getter
@Setter
@NoArgsConstructor
public class SaleStoreSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private Integer lineCount;

    @Column(nullable = false)
    private Integer unitCount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "commission1_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commission1Amount;

    @Column(name = "commission2_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commission2Amount;

    @Column(name = "commission_vat_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionIvaAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissionAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;
}
