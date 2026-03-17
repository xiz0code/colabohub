package com.colaborapp.promotions.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.products.domain.Product;
import com.colaborapp.stores.domain.Store;
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
@Table(name = "product_promotions")
@Getter
@Setter
@NoArgsConstructor
public class ProductPromotion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionType type;

    @Column(nullable = false)
    private Integer blockQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal blockPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentageDiscount;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private Instant startsAt;

    @Column
    private Instant endsAt;
}
