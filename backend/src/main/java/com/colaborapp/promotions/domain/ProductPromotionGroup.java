package com.colaborapp.promotions.domain;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.users.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "product_promotion_groups",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_promotion_groups_owner_name", columnNames = {"store_id", "owner_user_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductPromotionGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(nullable = false, length = 120)
    private String name;
}
