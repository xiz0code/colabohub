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
}
