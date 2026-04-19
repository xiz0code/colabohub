package com.colaborapp.markets.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.tenant.domain.Tenant;

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
@Table(name = "markets")
@Getter
@Setter
@NoArgsConstructor
public class Market extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 180)
    private String contactName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private boolean ufEnabled;

    @Column(nullable = false)
    private boolean active = true;

    @Column(precision = 19, scale = 4)
    private BigDecimal ufValue;

    @Column
    private Instant ufUpdatedAt;

    @Column(nullable = false)
    private boolean ufManualOverride = false;

    @Column(nullable = false)
    private boolean globalPromotionEnabled = false;

    @Column(precision = 5, scale = 2)
    private BigDecimal globalPromotionPercentage;

    @Column(nullable = false)
    private int lowStockAlertThreshold = 2;
}
