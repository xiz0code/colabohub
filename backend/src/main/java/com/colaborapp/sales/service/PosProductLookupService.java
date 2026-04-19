package com.colaborapp.sales.service;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.sales.web.dto.PosProductResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PosProductLookupService {

    private static final Logger log = LoggerFactory.getLogger(PosProductLookupService.class);

    private final ProductRepository productRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public PosProductResponse getByBarcode(String barcode) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = productRepository.findByBarcodeForPos(tenantId, barcode.trim(), ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active product matched barcode: " + barcode));
        accessControlService.requireMarketAccess(product.getStore().getMarket().getId());
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<PosProductResponse> search(String query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        List<Long> marketIds = accessControlService.currentMarketIds();
        if (marketIds.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = normalizeSearchPattern(query);
        log.info("POS search param type: {}", normalizedQuery.getClass().getName());
        return productRepository.searchByMarketIds(tenantId, marketIds, null, null, ProductStatus.ACTIVE, normalizedQuery, PageRequest.of(0, 10))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Product resolveForSale(String query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        String normalized = query.trim();
        String normalizedExact = normalized.toLowerCase(Locale.ROOT);
        String normalizedQuery = "%" + normalizedExact + "%";
        log.info("POS exact search param type: {}", normalizedExact.getClass().getName());
        Product product = productRepository.findByBarcodeForPos(tenantId, normalized, ProductStatus.ACTIVE)
                .or(() -> productRepository.findBySkuForPos(tenantId, normalizedExact, ProductStatus.ACTIVE))
                .or(() -> productRepository.searchByMarketIds(
                                tenantId,
                                accessControlService.currentMarketIds(),
                                null,
                                null,
                                ProductStatus.ACTIVE,
                                normalizedQuery,
                                PageRequest.of(0, 1))
                        .stream()
                        .findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("No active product matched the search query."));
        accessControlService.requireMarketAccess(product.getStore().getMarket().getId());
        return product;
    }

    private PosProductResponse toResponse(Product product) {
        String collaboratorName = product.getOwnerUser() != null && product.getOwnerUser().getFullName() != null && !product.getOwnerUser().getFullName().isBlank()
                ? product.getOwnerUser().getFullName()
                : "Sin colaborador";
        return new PosProductResponse(
                product.getId(),
                product.getStore().getId(),
                product.getStore().getName(),
                collaboratorName,
                product.getName(),
                product.getSku(),
                product.getBarcode(),
                product.getStock(),
                product.getSalePrice());
    }

    private String normalizeSearchPattern(String query) {
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
