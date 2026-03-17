package com.colaborapp.commissions.domain;

import java.math.BigDecimal;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.markets.domain.Market;
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
@Table(name = "commission_rules")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id")
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommissionRuleScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommissionType type;

    @Column(name = "commission_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionValue;

    @Column(nullable = false)
    private boolean active = true;
}
