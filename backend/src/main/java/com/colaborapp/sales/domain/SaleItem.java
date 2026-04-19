package com.colaborapp.sales.domain;

import java.math.BigDecimal;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.products.domain.Product;
import com.colaborapp.stores.domain.Store;

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
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
public class SaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 180)
    private String productNameSnapshot;

    @Column(nullable = false, length = 80)
    private String productSkuSnapshot;

    @Column(nullable = false, length = 64)
    private String productBarcodeSnapshot;

    @Column(nullable = false)
    private boolean manualEntry = false;

    @Column(length = 120)
    private String manualReference;

    @Column
    private Long collaboratorUserId;

    @Column(length = 180)
    private String collaboratorNameSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal baseUnitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lineBaseSubtotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal promotionDiscountAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleItemPricingType pricingType;

    @Column
    private Long appliedPromotionId;

    @Column(length = 180)
    private String appliedPromotionName;

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
