package com.colaborapp.promotions.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.User;

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
@Table(name = "promotion_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class PromotionCampaign extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PromotionType type;

    @Column
    private Integer blockQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal blockPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentageDiscount;

    @Column(precision = 19, scale = 4)
    private BigDecimal minimumPurchaseAmount;

    @Column(nullable = false)
    private boolean appliesToCash = false;

    @Column(nullable = false)
    private boolean appliesToDebit = false;

    @Column(nullable = false)
    private boolean appliesToCredit = false;

    @Column(nullable = false)
    private boolean appliesToTransfer = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private Instant startsAt;

    @Column
    private Instant endsAt;
}
