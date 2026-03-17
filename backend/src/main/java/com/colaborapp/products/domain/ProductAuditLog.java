package com.colaborapp.products.domain;

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
@Table(name = "product_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class ProductAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "field_name", nullable = false, length = 80)
    private String fieldName;

    @Column(name = "previous_value", length = 1024)
    private String previousValue;

    @Column(name = "new_value", length = 1024)
    private String newValue;
}
