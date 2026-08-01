package com.colaborapp.products.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.colaborapp.products.domain.Product;
import com.colaborapp.products.domain.ProductAuditLog;
import com.colaborapp.products.repository.ProductAuditLogRepository;
import com.colaborapp.products.web.dto.ProductAuditLogResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductAuditService {

    private final ProductAuditLogRepository productAuditLogRepository;

    @Transactional
    public void logChange(Product product, String fieldName, Object previousValue, Object newValue) {
        String previous = stringify(previousValue);
        String current = stringify(newValue);
        if (Objects.equals(previous, current)) {
            return;
        }

        ProductAuditLog log = new ProductAuditLog();
        log.setTenant(product.getTenant());
        log.setProduct(product);
        log.setFieldName(fieldName);
        log.setPreviousValue(previous);
        log.setNewValue(current);
        productAuditLogRepository.save(log);
    }

    @Transactional
    public void logStockIncrease(Product product, int previousStock, int quantityAdded, int newStock) {
        logStockEvent(product, "Stock agregado", previousStock, quantityAdded, newStock);
    }

    @Transactional
    public void logSale(Product product, int previousStock, int quantitySold, int newStock, Long saleId) {
        String previous = "Stock previo: " + previousStock;
        String current = "Vendido: " + quantitySold + " | Stock final: " + newStock + " | Venta: " + saleId;
        saveLog(product, "Vendido", previous, current);
    }

    @Transactional(readOnly = true)
    public List<ProductAuditLogResponse> getAuditTrail(Long productId) {
        return productAuditLogRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(log -> new ProductAuditLogResponse(
                        log.getId(),
                        log.getFieldName(),
                        log.getPreviousValue(),
                        log.getNewValue(),
                        log.getCreatedAt(),
                        log.getCreatedBy()))
                .toList();
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void logStockEvent(Product product, String fieldName, int previousStock, int quantityDelta, int newStock) {
        String previous = "Stock previo: " + previousStock;
        String current = "Subio: " + quantityDelta + " | Stock final: " + newStock;
        saveLog(product, fieldName, previous, current);
    }

    private void saveLog(Product product, String fieldName, String previousValue, String newValue) {
        ProductAuditLog log = new ProductAuditLog();
        log.setTenant(product.getTenant());
        log.setProduct(product);
        log.setFieldName(fieldName);
        log.setPreviousValue(previousValue);
        log.setNewValue(newValue);
        productAuditLogRepository.save(log);
    }
}
