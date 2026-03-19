package com.colaborapp.closings.domain;

import java.math.BigDecimal;

import com.colaborapp.common.domain.BaseEntity;

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
@Table(name = "monthly_closing_collaborators")
@Getter
@Setter
@NoArgsConstructor
public class MonthlyClosingCollaborator extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_closing_id", nullable = false)
    private MonthlyClosing monthlyClosing;

    @Column
    private Long collaboratorUserId;

    @Column(nullable = false, length = 180)
    private String collaboratorNameSnapshot;

    @Column(length = 180)
    private String collaboratorEmailSnapshot;

    @Column(nullable = false)
    private boolean factura;

    @Column(nullable = false)
    private Long saleCount;

    @Column(nullable = false)
    private Long totalItems;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalSalesAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCommissionAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalIvaAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal ivaToPayAmount;
}
