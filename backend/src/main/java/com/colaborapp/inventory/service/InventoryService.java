package com.colaborapp.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.common.exception.ResourceNotFoundException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.domain.StockMovement;
import com.colaborapp.inventory.domain.StockMovementType;
import com.colaborapp.inventory.repository.StockMovementRepository;
import com.colaborapp.inventory.web.dto.StockAdjustmentRequest;
import com.colaborapp.inventory.web.dto.StockMovementResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;
import com.colaborapp.products.domain.Product;
import com.colaborapp.products.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;

    @Transactional
    public StockMovementResponse adjustStock(StockAdjustmentRequest request) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR);
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = getProductEntity(request.productId(), tenantId);
        requireProductInventoryWriteAccess(product);
        return applyAdjustment(product, request.quantityDelta(), request.reason().trim(), product.getId());
    }

    @Transactional
    public StockMovementResponse addStock(Product product, int quantityDelta, String reason) {
        return applyAdjustment(product, quantityDelta, reason, product.getId());
    }

    private StockMovementResponse applyAdjustment(Product product, int quantityDelta, String reason, Long referenceId) {
        if (quantityDelta == 0) {
            throw new BusinessException("La cantidad de ajuste debe ser distinta de 0.");
        }

        int previousStock = product.getStock();
        int newStock = previousStock + quantityDelta;

        if (newStock < 0) {
            throw new BusinessException("La reduccion solicitada dejaria el stock en negativo.");
        }

        product.setStock(newStock);
        productRepository.save(product);

        StockMovement movement = createMovement(
                product,
                quantityDelta,
                previousStock,
                newStock,
                reason,
                referenceId);

        return toResponse(stockMovementRepository.save(movement));
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getProductMovements(Long productId) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        Product product = getProductEntity(productId, tenantId);
        requireProductReadAccess(product);

        return stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void registerInitialStock(Product product, int initialStock) {
        StockMovement movement = createMovement(product, initialStock, 0, initialStock, "INITIAL_STOCK", product.getId());
        stockMovementRepository.save(movement);
    }

    private Product getProductEntity(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private StockMovement createMovement(
            Product product,
            int quantity,
            int previousStock,
            int newStock,
            String referenceType,
            Long referenceId) {
        StockMovement movement = new StockMovement();
        movement.setTenant(product.getTenant());
        movement.setProduct(product);
        movement.setStore(product.getStore());
        movement.setType(StockMovementType.ADJUSTMENT);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        return movement;
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getStore().getId(),
                movement.getStore().getName(),
                movement.getType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getCreatedAt(),
                movement.getCreatedBy());
    }

    private void requireProductReadAccess(Product product) {
        if (accessControlService.hasRole(RoleCode.STORE_USER)) {
            Long currentUserId = accessControlService.getCurrentUser().user().getId();
            Long ownerUserId = product.getOwnerUser() != null ? product.getOwnerUser().getId() : null;
            if (currentUserId != null && currentUserId.equals(ownerUserId)) {
                return;
            }
        }

        accessControlService.requireStoreAccess(product.getStore().getId());
    }

    private void requireProductInventoryWriteAccess(Product product) {
        if (product.getStore() != null && product.getStore().getMarket() != null && product.getStore().getMarket().getId() != null) {
            accessControlService.requireMarketAccess(product.getStore().getMarket().getId());
            return;
        }
        accessControlService.requireStoreAccess(product.getStore().getId());
    }
}
