package com.colaborapp.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.colaborapp.markets.domain.Market;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.promotions.domain.ProductPromotion;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.promotions.repository.ProductPromotionRepository;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.Sale;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleItemPricingType;
import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.stores.domain.Store;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.domain.StoreType;
import com.colaborapp.tenant.domain.Tenant;

@ExtendWith(MockitoExtension.class)
class PosPricingServiceTest {

    @Mock
    private ProductPromotionRepository productPromotionRepository;

    @Mock
    private CommissionSettingsService commissionSettingsService;

    private PosPricingService posPricingService;
    private Tenant tenant;
    private Market market;
    private Store storeAna;
    private Store storeLucia;

    @BeforeEach
    void setUp() {
        posPricingService = new PosPricingService(productPromotionRepository, commissionSettingsService);
        tenant = new Tenant();
        tenant.setId(1L);

        market = new Market();
        market.setId(10L);
        market.setTenant(tenant);
        market.setName("Mercado Creativo");
        market.setEmail("admin@mercado.cl");
        market.setCity("Santiago");
        market.setCurrency("CLP");
        market.setUfEnabled(true);

        storeAna = store(1L, "Tienda Ana");
        storeLucia = store(2L, "Tienda Lucia");

        when(commissionSettingsService.getEffectiveCommissionConfig(List.of(10L))).thenReturn(
                java.util.Map.of(10L, new CommissionSettingsService.EffectiveCommissionConfig(
                        CommissionSettingsService.DEFAULT_COMMISSION_UF,
                        CommissionSettingsService.DEFAULT_COMMISSION_PERCENTAGE)));
    }

    @Test
    void shouldApplyCommissionsForDebito() {
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of());

        SaleItem item = saleItem(product(100L, storeAna, "Producto A", new BigDecimal("10000.00")), 1);

        PosPricingService.RecalculationResult result = posPricingService.calculateSalePricing(
                List.of(item),
                PaymentMethod.DEBITO,
                new BigDecimal("40000.00"));

        assertThat(result.totalCommissionAmount()).isEqualByComparingTo("175");
        assertThat(item.getCommission1Amount()).isEqualByComparingTo("68");
        assertThat(item.getCommission2Amount()).isEqualByComparingTo("79");
        assertThat(item.getCommissionIvaAmount()).isEqualByComparingTo("28");
        assertThat(item.getTotalCommissionAmount()).isEqualByComparingTo("175");
        assertThat(item.getNetAmount()).isEqualByComparingTo("9825.00");
    }

    @Test
    void shouldLeaveCommissionsInZeroForNonDebito() {
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of());

        SaleItem item = saleItem(product(101L, storeAna, "Producto A", new BigDecimal("10000.00")), 2);

        PosPricingService.RecalculationResult result = posPricingService.calculateSalePricing(
                List.of(item),
                PaymentMethod.CASH,
                null);

        assertThat(item.getCommission1Amount()).isEqualByComparingTo("0");
        assertThat(item.getCommission2Amount()).isEqualByComparingTo("0");
        assertThat(item.getCommissionIvaAmount()).isEqualByComparingTo("0");
        assertThat(item.getTotalCommissionAmount()).isEqualByComparingTo("0");
        assertThat(result.totalCommissionAmount()).isEqualByComparingTo("0");
        assertThat(item.getNetAmount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void shouldCalculateCommission1PerStoreUsingOnlyItemsFromThatStore() {
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of());

        SaleItem anaItemOne = saleItem(product(201L, storeAna, "Aro Flor", new BigDecimal("10000.00")), 1);
        SaleItem anaItemTwo = saleItem(product(202L, storeAna, "Pulsera Rosa", new BigDecimal("12000.00")), 1);
        SaleItem luciaItem = saleItem(product(203L, storeLucia, "Collar Luna", new BigDecimal("8000.00")), 1);

        PosPricingService.RecalculationResult result = posPricingService.calculateSalePricing(
                List.of(anaItemOne, anaItemTwo, luciaItem),
                PaymentMethod.DEBITO,
                new BigDecimal("10000.00"));

        assertThat(anaItemOne.getCommission1Amount()).isEqualByComparingTo("8");
        assertThat(anaItemTwo.getCommission1Amount()).isEqualByComparingTo("8");
        assertThat(luciaItem.getCommission1Amount()).isEqualByComparingTo("17");
        assertThat(result.summaries()).hasSize(2);
        assertThat(result.summaries().stream()
                .filter(summary -> summary.getStore().getId().equals(storeAna.getId()))
                .findFirst()
                .orElseThrow()
                .getCommission1Amount()).isEqualByComparingTo("16");
    }

    @Test
    void shouldRoundClpCommissionsUsingHalfUpScaleZero() {
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of());

        SaleItem item = saleItem(product(301L, storeAna, "Producto A", new BigDecimal("500.00")), 1);

        posPricingService.calculateSalePricing(
                List.of(item),
                PaymentMethod.DEBITO,
                new BigDecimal("0.00"));

        assertThat(item.getCommission2Amount()).isEqualByComparingTo("4");
        assertThat(item.getCommissionIvaAmount()).isEqualByComparingTo("1");
        assertThat(item.getTotalCommissionAmount()).isEqualByComparingTo("5");
        assertThat(item.getCommission2Amount().scale()).isEqualTo(0);
        assertThat(item.getCommissionIvaAmount().scale()).isEqualTo(0);
        assertThat(item.getTotalCommissionAmount().scale()).isEqualTo(0);
    }

    @Test
    void shouldApplyPercentagePromotionWhenNoGlobalPromotionExists() {
        ProductPromotion promotion = new ProductPromotion();
        promotion.setId(11L);
        promotion.setProduct(product(401L, storeAna, "Sticker BTS", new BigDecimal("2000.00")));
        promotion.setType(PromotionType.PERCENTAGE_DISCOUNT);
        promotion.setPercentageDiscount(new BigDecimal("30.00"));
        promotion.setName("30% descuento");
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of(promotion));

        SaleItem item = saleItem(product(401L, storeAna, "Sticker BTS", new BigDecimal("2000.00")), 2);

        PosPricingService.RecalculationResult result = posPricingService.calculateSalePricing(
                List.of(item),
                PaymentMethod.CASH,
                null);

        assertThat(item.getSubtotal()).isEqualByComparingTo("2800.00");
        assertThat(item.getPromotionDiscountAmount()).isEqualByComparingTo("1200.00");
        assertThat(item.getAppliedPromotionName()).isEqualTo("30% descuento");
        assertThat(result.totalAmount()).isEqualByComparingTo("2800.00");
    }

    @Test
    void shouldUseGlobalPromotionInsteadOfProductPromotion() {
        market.setGlobalPromotionEnabled(true);
        market.setGlobalPromotionPercentage(new BigDecimal("25.00"));

        ProductPromotion promotion = new ProductPromotion();
        promotion.setId(12L);
        promotion.setProduct(product(402L, storeAna, "Llaveros", new BigDecimal("2000.00")));
        promotion.setType(PromotionType.QUANTITY_BLOCK);
        promotion.setBlockQuantity(3);
        promotion.setBlockPrice(new BigDecimal("4000.00"));
        promotion.setName("3 x 4000");
        when(productPromotionRepository.findActiveByProductIds(any(), any())).thenReturn(List.of(promotion));

        SaleItem item = saleItem(product(402L, storeAna, "Llaveros", new BigDecimal("2000.00")), 3);

        posPricingService.calculateSalePricing(List.of(item), PaymentMethod.CASH, null);

        assertThat(item.getSubtotal()).isEqualByComparingTo("4500.00");
        assertThat(item.getAppliedPromotionName()).isEqualTo("Promocion global 25%");
        assertThat(item.getAppliedPromotionId()).isNull();
    }

    private Store store(Long id, String name) {
        Store store = new Store();
        store.setId(id);
        store.setTenant(tenant);
        store.setMarket(market);
        store.setCode(name.toUpperCase().replace(" ", "-"));
        store.setName(name);
        store.setType(StoreType.COLLABORATOR);
        store.setStatus(StoreStatus.ACTIVE);
        return store;
    }

    private Product product(Long id, Store store, String name, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setTenant(tenant);
        product.setStore(store);
        product.setName(name);
        product.setSku(name.toUpperCase().replace(" ", "-"));
        product.setBarcode("7500000000" + id);
        product.setSalePrice(price);
        product.setStatus(ProductStatus.ACTIVE);
        product.setStock(10);
        product.setVersion(0L);
        return product;
    }

    private SaleItem saleItem(Product product, int quantity) {
        Sale sale = new Sale();
        sale.setId(1L);
        sale.setTenant(tenant);

        SaleItem item = new SaleItem();
        item.setSale(sale);
        item.setProduct(product);
        item.setStore(product.getStore());
        item.setProductNameSnapshot(product.getName());
        item.setProductSkuSnapshot(product.getSku());
        item.setProductBarcodeSnapshot(product.getBarcode());
        item.setQuantity(quantity);
        item.setBaseUnitPrice(product.getSalePrice());
        item.setLineBaseSubtotal(product.getSalePrice().multiply(BigDecimal.valueOf(quantity)));
        item.setPromotionDiscountAmount(BigDecimal.ZERO.setScale(2));
        item.setSubtotal(product.getSalePrice().multiply(BigDecimal.valueOf(quantity)));
        item.setPricingType(SaleItemPricingType.NORMAL);
        item.setCommission1Amount(BigDecimal.ZERO.setScale(0));
        item.setCommission2Amount(BigDecimal.ZERO.setScale(0));
        item.setCommissionIvaAmount(BigDecimal.ZERO.setScale(0));
        item.setTotalCommissionAmount(BigDecimal.ZERO.setScale(0));
        item.setNetAmount(BigDecimal.ZERO.setScale(2));
        return item;
    }
}
