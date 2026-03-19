package com.colaborapp.sales.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.sales.web.dto.PosProductResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PosProductLookupService {

    private final ProductRepository productRepository;
    private final CurrentTenantProvider currentTenantProvider;

    @Transactional(readOnly = true)
    public PosProductResponse getByBarcode(String barcode) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = productRepository.findByBarcodeForPos(tenantId, barcode.trim(), ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active product matched barcode: " + barcode));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<PosProductResponse> search(String query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        return productRepository.searchForPos(tenantId, query.trim(), ProductStatus.ACTIVE, PageRequest.of(0, 10))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Product resolveForSale(String query) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        String normalized = query.trim();
        return productRepository.findByBarcodeForPos(tenantId, normalized, ProductStatus.ACTIVE)
                .or(() -> productRepository.findBySkuForPos(tenantId, normalized, ProductStatus.ACTIVE))
                .or(() -> productRepository.searchForPos(tenantId, normalized, ProductStatus.ACTIVE, PageRequest.of(0, 1))
                        .stream()
                        .findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("No active product matched the search query."));
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
}
