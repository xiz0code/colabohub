package com.colaborapp.sales.service;

import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.colaborapp.promotions.domain.ProductPromotion;
import com.colaborapp.promotions.domain.PromotionType;
import com.colaborapp.promotions.repository.ProductPromotionRepository;
import com.colaborapp.sales.domain.PaymentMethod;
import com.colaborapp.sales.domain.SaleItem;
import com.colaborapp.sales.domain.SaleItemPricingType;
import com.colaborapp.sales.domain.SaleStoreSummary;
import com.colaborapp.settings.service.CommissionSettingsService;
import com.colaborapp.stores.domain.Store;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PosPricingService {

    private static final int MONEY_SCALE = 2;
    private static final int CLP_SCALE = 0;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, HALF_UP);
    private static final BigDecimal ZERO_CLP = BigDecimal.ZERO.setScale(CLP_SCALE, HALF_UP);
    private static final BigDecimal VAT_RATE = new BigDecimal("0.19");

    private final ProductPromotionRepository productPromotionRepository;
    private final CommissionSettingsService commissionSettingsService;

    public RecalculationResult calculateSalePricing(
            List<SaleItem> items,
            PaymentMethod paymentMethod,
            BigDecimal ufValue) {
        if (items.isEmpty()) {
            return RecalculationResult.empty();
        }

        Map<Long, List<ProductPromotion>> promotionsByProductId = productPromotionRepository
                .findActiveByProductIds(items.stream().map(item -> item.getProduct().getId()).distinct().toList(), Instant.now())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(pp -> pp.getProduct().getId()));

        List<SaleItem> recalculatedItems = new ArrayList<>(items.size());
        for (SaleItem item : items) {
            calculateItemPricing(item, promotionsByProductId.getOrDefault(item.getProduct().getId(), List.of()));
            recalculatedItems.add(item);
        }

        List<SaleStoreSummary> summaries = calculateStoreSummaries(recalculatedItems, paymentMethod, ufValue);
        BigDecimal subtotalAmount = recalculatedItems.stream()
                .map(SaleItem::getLineBaseSubtotal)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalDiscountAmount = recalculatedItems.stream()
                .map(SaleItem::getPromotionDiscountAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalAmount = recalculatedItems.stream()
                .map(SaleItem::getSubtotal)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalCommissionAmount = recalculatedItems.stream()
                .map(SaleItem::getTotalCommissionAmount)
                .reduce(ZERO_CLP, BigDecimal::add);
        BigDecimal totalNetAmount = recalculatedItems.stream()
                .map(SaleItem::getNetAmount)
                .reduce(ZERO, BigDecimal::add);

        return new RecalculationResult(
                scale(subtotalAmount),
                scale(totalDiscountAmount),
                scale(totalAmount),
                roundClp(totalCommissionAmount),
                scale(totalNetAmount),
                summaries);
    }

    public void calculateItemPricing(SaleItem item, List<ProductPromotion> promotions) {
        PriceComputation computation = computeBestPrice(item, promotions);
        applyPrice(item, computation);
    }

    public List<SaleStoreSummary> calculateStoreSummaries(
            List<SaleItem> items,
            PaymentMethod paymentMethod,
            BigDecimal ufValue) {
        return computeStoreSummaries(items, paymentMethod, ufValue);
    }

    private PriceComputation computeBestPrice(SaleItem item, List<ProductPromotion> promotions) {
        BigDecimal unitPrice = scale(item.getProduct().getSalePrice());
        int quantity = item.getQuantity();
        BigDecimal globalPromotionPercentage = item.getProduct().getStore().getMarket().isGlobalPromotionEnabled()
                ? item.getProduct().getStore().getMarket().getGlobalPromotionPercentage()
                : null;

        if (globalPromotionPercentage != null && globalPromotionPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountFactor = BigDecimal.ONE.subtract(globalPromotionPercentage.divide(new BigDecimal("100"), 4, HALF_UP));
            BigDecimal discountedUnitPrice = scale(unitPrice.multiply(discountFactor));
            BigDecimal baseSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal subtotal = scale(discountedUnitPrice.multiply(BigDecimal.valueOf(quantity)));
            BigDecimal discount = scale(baseSubtotal.subtract(subtotal));
            return new PriceComputation(
                    scale(unitPrice),
                    scale(baseSubtotal),
                    discount,
                    subtotal,
                    Map.of(),
                    quantity,
                    globalPromotionPercentage,
                    null);
        }

        ProductPromotion percentagePromotion = promotions.stream()
                .filter(promotion -> promotion.getType() == PromotionType.PERCENTAGE_DISCOUNT)
                .filter(promotion -> promotion.getPercentageDiscount() != null)
                .max(Comparator.comparing(ProductPromotion::getPercentageDiscount))
                .orElse(null);

        if (percentagePromotion != null && percentagePromotion.getPercentageDiscount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountFactor = BigDecimal.ONE.subtract(percentagePromotion.getPercentageDiscount().divide(new BigDecimal("100"), 4, HALF_UP));
            BigDecimal discountedUnitPrice = scale(unitPrice.multiply(discountFactor));
            BigDecimal baseSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal subtotal = scale(discountedUnitPrice.multiply(BigDecimal.valueOf(quantity)));
            BigDecimal discount = scale(baseSubtotal.subtract(subtotal));
            return new PriceComputation(
                    scale(unitPrice),
                    scale(baseSubtotal),
                    discount,
                    subtotal,
                    Map.of(),
                    0,
                    null,
                    percentagePromotion);
        }

        BigDecimal[] dp = new BigDecimal[quantity + 1];
        Choice[] choices = new Choice[quantity + 1];
        dp[0] = ZERO;

        List<ProductPromotion> quantityPromotions = promotions.stream()
                .filter(promotion -> promotion.getType() == PromotionType.QUANTITY_BLOCK)
                .sorted(Comparator.comparing(ProductPromotion::getBlockQuantity).reversed()
                        .thenComparing(ProductPromotion::getBlockPrice))
                .toList();

        for (int current = 1; current <= quantity; current++) {
            dp[current] = dp[current - 1].add(unitPrice);
            choices[current] = new Choice(null, 1, unitPrice, true);

            for (ProductPromotion promotion : quantityPromotions) {
                int blockQty = promotion.getBlockQuantity();
                if (current < blockQty) {
                    continue;
                }

                BigDecimal candidate = dp[current - blockQty].add(scale(promotion.getBlockPrice()));
                if (candidate.compareTo(dp[current]) < 0) {
                    dp[current] = candidate;
                    choices[current] = new Choice(promotion, blockQty, scale(promotion.getBlockPrice()), false);
                }
            }
        }

        Map<ProductPromotion, Integer> promotionsUsed = new HashMap<>();
        int unitCount = 0;
        int cursor = quantity;
        while (cursor > 0) {
            Choice choice = choices[cursor];
            if (choice.unit()) {
                unitCount += 1;
            } else {
                promotionsUsed.merge(choice.promotion(), 1, Integer::sum);
            }
            cursor -= choice.quantity();
        }

        BigDecimal baseSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal subtotal = scale(dp[quantity]);
        BigDecimal discount = scale(baseSubtotal.subtract(subtotal));
        return new PriceComputation(
                scale(unitPrice),
                scale(baseSubtotal),
                discount,
                subtotal,
                promotionsUsed,
                unitCount,
                null,
                null);
    }

    private void applyPrice(SaleItem item, PriceComputation computation) {
        item.setProductNameSnapshot(item.getProduct().getName());
        item.setProductSkuSnapshot(item.getProduct().getSku());
        item.setProductBarcodeSnapshot(item.getProduct().getBarcode());
        item.setStore(item.getProduct().getStore());
        item.setBaseUnitPrice(computation.baseUnitPrice());
        item.setLineBaseSubtotal(computation.baseSubtotal());
        item.setPromotionDiscountAmount(computation.discountAmount());
        item.setSubtotal(computation.finalSubtotal());

        if (computation.globalPromotionPercentage() != null) {
            item.setPricingType(SaleItemPricingType.PROMOTION);
            item.setAppliedPromotionId(null);
            item.setAppliedPromotionName("Promocion global " + computation.globalPromotionPercentage().stripTrailingZeros().toPlainString() + "%");
            return;
        }

        if (computation.percentagePromotion() != null) {
            item.setPricingType(SaleItemPricingType.PROMOTION);
            item.setAppliedPromotionId(computation.percentagePromotion().getId());
            item.setAppliedPromotionName(computation.percentagePromotion().getName());
            return;
        }

        if (computation.promotionsUsed().isEmpty()) {
            item.setPricingType(SaleItemPricingType.NORMAL);
            item.setAppliedPromotionId(null);
            item.setAppliedPromotionName(null);
            return;
        }

        item.setPricingType(SaleItemPricingType.PROMOTION);
        if (computation.promotionsUsed().size() == 1 && computation.unitCount() == 0) {
            ProductPromotion promotion = computation.promotionsUsed().keySet().iterator().next();
            item.setAppliedPromotionId(promotion.getId());
            item.setAppliedPromotionName(promotion.getName());
            return;
        }

        item.setAppliedPromotionId(null);
        item.setAppliedPromotionName(buildPromotionSummary(computation.promotionsUsed(), computation.unitCount()));
    }

    private String buildPromotionSummary(Map<ProductPromotion, Integer> promotionsUsed, int unitCount) {
        List<String> parts = new ArrayList<>();
        promotionsUsed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ProductPromotion::getBlockQuantity).reversed()))
                .forEach(entry -> parts.add(entry.getValue() + "x " + entry.getKey().getName()));
        if (unitCount > 0) {
            parts.add(unitCount + " unidad(es) precio normal");
        }
        return String.join(" + ", parts);
    }

    private List<SaleStoreSummary> computeStoreSummaries(
            List<SaleItem> items,
            PaymentMethod paymentMethod,
            BigDecimal ufValue) {
        Map<Long, List<SaleItem>> itemsByStore = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getStore().getId()));
        Map<Long, CommissionSettingsService.EffectiveCommissionConfig> commissionsByMarket = commissionSettingsService.getEffectiveCommissionConfig(
                items.stream()
                        .map(item -> item.getStore().getMarket().getId())
                        .distinct()
                        .toList());

        List<SaleStoreSummary> summaries = new ArrayList<>();
        for (List<SaleItem> storeItems : itemsByStore.values()) {
            Store store = storeItems.getFirst().getStore();
            int lineCount = storeItems.size();
            CommissionSettingsService.EffectiveCommissionConfig commissionConfig = commissionsByMarket.getOrDefault(
                    store.getMarket().getId(),
                    new CommissionSettingsService.EffectiveCommissionConfig(
                            CommissionSettingsService.DEFAULT_COMMISSION_UF,
                            CommissionSettingsService.DEFAULT_COMMISSION_PERCENTAGE));

            BigDecimal commission1PerItem = ZERO_CLP;
            if (appliesDebitCommissions(paymentMethod)) {
                BigDecimal commission1StoreTotal = scale(ufValue.multiply(commissionConfig.commissionUfValue()));
                commission1PerItem = roundClp(
                        commission1StoreTotal.divide(BigDecimal.valueOf(lineCount), MONEY_SCALE, HALF_UP));
            }

            BigDecimal subtotal = ZERO;
            BigDecimal commission1 = ZERO_CLP;
            BigDecimal commission2 = ZERO_CLP;
            BigDecimal commissionIva = ZERO_CLP;
            BigDecimal totalCommission = ZERO_CLP;
            BigDecimal net = ZERO;
            int unitCount = 0;

            for (SaleItem item : storeItems) {
                unitCount += item.getQuantity();
                if (appliesDebitCommissions(paymentMethod)) {
                    BigDecimal commission2Amount = roundClp(item.getSubtotal().multiply(commissionConfig.commissionPercentageValue()));
                    BigDecimal vatAmount = roundClp(commission1PerItem.add(commission2Amount).multiply(VAT_RATE));
                    BigDecimal totalCommissionAmount = roundClp(commission1PerItem.add(commission2Amount).add(vatAmount));
                    BigDecimal netAmount = scale(item.getSubtotal().subtract(totalCommissionAmount));

                    item.setCommission1Amount(commission1PerItem);
                    item.setCommission2Amount(commission2Amount);
                    item.setCommissionIvaAmount(vatAmount);
                    item.setTotalCommissionAmount(totalCommissionAmount);
                    item.setNetAmount(netAmount);
                } else {
                    item.setCommission1Amount(ZERO_CLP);
                    item.setCommission2Amount(ZERO_CLP);
                    item.setCommissionIvaAmount(ZERO_CLP);
                    item.setTotalCommissionAmount(ZERO_CLP);
                    item.setNetAmount(scale(item.getSubtotal()));
                }

                subtotal = subtotal.add(item.getSubtotal());
                commission1 = commission1.add(item.getCommission1Amount());
                commission2 = commission2.add(item.getCommission2Amount());
                commissionIva = commissionIva.add(item.getCommissionIvaAmount());
                totalCommission = totalCommission.add(item.getTotalCommissionAmount());
                net = net.add(item.getNetAmount());
            }

            SaleStoreSummary summary = new SaleStoreSummary();
            summary.setStore(store);
            summary.setLineCount(lineCount);
            summary.setUnitCount(unitCount);
            summary.setCommission1Amount(ZERO_CLP);
            summary.setCommission2Amount(ZERO_CLP);
            summary.setCommissionIvaAmount(ZERO_CLP);
            summary.setTotalCommissionAmount(ZERO_CLP);
            summary.setSubtotalAmount(scale(subtotal));
            summary.setCommission1Amount(roundClp(commission1));
            summary.setCommission2Amount(roundClp(commission2));
            summary.setCommissionIvaAmount(roundClp(commissionIva));
            summary.setTotalCommissionAmount(roundClp(totalCommission));
            summary.setNetAmount(scale(net));
            summaries.add(summary);
        }

        return summaries.stream()
                .sorted(Comparator.comparing(summary -> summary.getStore().getName()))
                .toList();
    }

    private boolean appliesDebitCommissions(PaymentMethod paymentMethod) {
        return paymentMethod == PaymentMethod.DEBITO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, HALF_UP);
    }

    private BigDecimal roundClp(BigDecimal value) {
        return value.setScale(CLP_SCALE, HALF_UP);
    }

    public record RecalculationResult(
            BigDecimal subtotalAmount,
            BigDecimal totalDiscountAmount,
            BigDecimal totalAmount,
            BigDecimal totalCommissionAmount,
            BigDecimal totalNetAmount,
            List<SaleStoreSummary> summaries) {

        static RecalculationResult empty() {
            return new RecalculationResult(ZERO, ZERO, ZERO, ZERO_CLP, ZERO, List.of());
        }
    }

    private record Choice(
            ProductPromotion promotion,
            int quantity,
            BigDecimal value,
            boolean unit) {
    }

    private record PriceComputation(
            BigDecimal baseUnitPrice,
            BigDecimal baseSubtotal,
            BigDecimal discountAmount,
            BigDecimal finalSubtotal,
            Map<ProductPromotion, Integer> promotionsUsed,
            int unitCount,
            BigDecimal globalPromotionPercentage,
            ProductPromotion percentagePromotion) {
    }
}
