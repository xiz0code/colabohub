package com.colaborapp.sales.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.colaborapp.common.domain.BaseEntity;
import com.colaborapp.markets.domain.Market;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleStatus;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.tenant.domain.Tenant;
import com.colaborapp.tenant.domain.TenantStatus;

@DataJpaTest
class SaleRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SaleRepository saleRepository;

    private Tenant tenant;
    private Market market;
    private Store storeAna;
    private Store storeLucia;
    private Instant startAt;
    private Instant endAt;

    @BeforeEach
    void setUp() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        startAt = date.atStartOfDay(ZoneId.of("America/Santiago")).toInstant();
        endAt = date.plusDays(1).atStartOfDay(ZoneId.of("America/Santiago")).toInstant();

        tenant = persistTenant("tenant-report");
        market = persistMarket("Mercado Reportes", tenant);
        storeAna = persistStore("ANA-REPORT", "Tienda Ana", market, tenant);
        storeLucia = persistStore("LUCIA-REPORT", "Tienda Lucia", market, tenant);
    }

    @Test
    void summarizeByPeriodShouldNotDuplicateTotalAmountWhenSaleHasMultipleStoreSummaries() {
        Sale sale = persistSale("S-2026-01000001", market, tenant, SaleStatus.CONFIRMED, startAt.plusSeconds(3600));
        persistSummary(sale, storeAna, "20000.0000", "1000.0000", "19000.0000", 2, 2);
        persistSummary(sale, storeLucia, "15000.0000", "800.0000", "14200.0000", 1, 1);
        entityManager.flush();

        Object[] row = unwrapRow(saleRepository.summarizeByPeriod(tenant.getId(), SaleStatus.CONFIRMED, startAt, endAt));

        assertThat(((Number) row[0]).longValue()).isEqualTo(1L);
        assertThat((BigDecimal) row[1]).isEqualByComparingTo("35000.0000");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("35000.0000");
        assertThat((BigDecimal) row[3]).isEqualByComparingTo("1800.0000");
        assertThat((BigDecimal) row[4]).isEqualByComparingTo("33200.0000");
    }

    @Test
    void summarizeMarketPayoutsByPeriodShouldOnlyIncludeConfirmedSales() {
        Sale confirmed = persistSale("S-2026-01000002", market, tenant, SaleStatus.CONFIRMED, startAt.plusSeconds(7200));
        persistSummary(confirmed, storeAna, "18000.0000", "900.0000", "17100.0000", 1, 1);

        Sale openSale = persistSale("S-2026-01000003", market, tenant, SaleStatus.OPEN, null);
        persistSummary(openSale, storeAna, "99999.0000", "999.0000", "99000.0000", 1, 1);
        entityManager.flush();

        var rows = saleRepository.summarizeMarketPayoutsByPeriod(
                tenant.getId(),
                market.getId(),
                SaleStatus.CONFIRMED,
                startAt,
                endAt);

        assertThat(rows).hasSize(1);
        assertThat((Long) rows.getFirst()[0]).isEqualTo(storeAna.getId());
        assertThat((BigDecimal) rows.getFirst()[3]).isEqualByComparingTo("18000.0000");
        assertThat((BigDecimal) rows.getFirst()[4]).isEqualByComparingTo("900.0000");
        assertThat((BigDecimal) rows.getFirst()[5]).isEqualByComparingTo("17100.0000");
    }

    @Test
    void summarizeStoreShouldReturnCorrectTotals() {
        Sale saleOne = persistSale("S-2026-01000004", market, tenant, SaleStatus.CONFIRMED, startAt.plusSeconds(1000));
        Sale saleTwo = persistSale("S-2026-01000005", market, tenant, SaleStatus.CONFIRMED, startAt.plusSeconds(2000));
        persistSummary(saleOne, storeAna, "12000.0000", "700.0000", "11300.0000", 1, 1);
        persistSummary(saleTwo, storeAna, "15000.0000", "900.0000", "14100.0000", 1, 2);
        entityManager.flush();

        Object[] row = unwrapRow(saleRepository.summarizeStore(tenant.getId(), storeAna.getId(), SaleStatus.CONFIRMED));

        assertThat(((Number) row[0]).longValue()).isEqualTo(2L);
        assertThat((BigDecimal) row[1]).isEqualByComparingTo("27000.0000");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("1600.0000");
        assertThat((BigDecimal) row[3]).isEqualByComparingTo("25400.0000");
    }

    private Tenant persistTenant(String code) {
        Tenant persisted = new Tenant();
        persisted.setCode(code);
        persisted.setName("Tenant " + code);
        persisted.setStatus(TenantStatus.ACTIVE);
        stamp(persisted);
        entityManager.persist(persisted);
        return persisted;
    }

    private Market persistMarket(String name, Tenant tenant) {
        Market persisted = new Market();
        persisted.setTenant(tenant);
        persisted.setName(name);
        persisted.setEmail("reportes@" + name.toLowerCase().replace(" ", "") + ".cl");
        persisted.setCity("Santiago");
        persisted.setCurrency("CLP");
        persisted.setUfEnabled(true);
        stamp(persisted);
        entityManager.persist(persisted);
        return persisted;
    }

    private Store persistStore(String code, String name, Market market, Tenant tenant) {
        Store persisted = new Store();
        persisted.setTenant(tenant);
        persisted.setMarket(market);
        persisted.setCode(code);
        persisted.setName(name);
        persisted.setType(StoreType.COLLABORATOR);
        persisted.setStatus(StoreStatus.ACTIVE);
        stamp(persisted);
        entityManager.persist(persisted);
        return persisted;
    }

    private Sale persistSale(String saleNumber, Market market, Tenant tenant, SaleStatus status, Instant confirmedAt) {
        Sale sale = new Sale();
        sale.setTenant(tenant);
        sale.setMarket(market);
        sale.setSaleNumber(saleNumber);
        sale.setStatus(status);
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setSubtotalAmount(new BigDecimal("0.0000"));
        sale.setTotalDiscountAmount(new BigDecimal("0.0000"));
        sale.setTotalAmount(new BigDecimal("0.0000"));
        sale.setTotalCommissionAmount(new BigDecimal("0.0000"));
        sale.setTotalNetAmount(new BigDecimal("0.0000"));
        sale.setOpenedAt(startAt);
        sale.setConfirmedAt(confirmedAt);
        stamp(sale);
        entityManager.persist(sale);
        return sale;
    }

    private void persistSummary(Sale sale, Store store, String subtotal, String commission, String net, int lines, int units) {
        SaleStoreSummary summary = new SaleStoreSummary();
        summary.setSale(sale);
        summary.setStore(store);
        summary.setLineCount(lines);
        summary.setUnitCount(units);
        summary.setSubtotalAmount(new BigDecimal(subtotal));
        summary.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        summary.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        summary.setTotalCommissionAmount(new BigDecimal(commission));
        summary.setNetAmount(new BigDecimal(net));
        stamp(summary);
        entityManager.persist(summary);
    }

    private void stamp(BaseEntity entity) {
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setCreatedBy("test");
        entity.setUpdatedBy("test");
    }

    private Object[] unwrapRow(Object[] row) {
        if (row.length == 1 && row[0] instanceof Object[] nested) {
            return nested;
        }
        return row;
    }
}
