package com.colaborapp.pickups.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.sales.domain.Sale;
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
@Table(name = "pickups")
@Getter
@Setter
@NoArgsConstructor
public class Pickup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_sale_id")
    private Sale linkedSale;

    @Column(nullable = false, length = 80)
    private String pickupNumber;

    @Column(nullable = false, length = 180)
    private String customerName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private boolean payable;

    @Column(precision = 19, scale = 4)
    private BigDecimal amountDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PickupStatus status;

    @Column
    private Instant collectedAt;

    @Column(length = 180)
    private String collectedBy;
}
