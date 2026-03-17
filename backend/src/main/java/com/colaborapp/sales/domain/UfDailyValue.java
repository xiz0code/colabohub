package com.colaborapp.sales.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
@Table(name = "uf_daily_values")
@Getter
@Setter
@NoArgsConstructor
public class UfDailyValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "uf_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal ufValue;

    @Column(length = 120)
    private String source;
}
