package com.colaborapp.products.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.colaborapp.common.exception.BusinessException;
import com.colaborapp.config.bootstrap.CurrentTenantProvider;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.inventory.web.dto.StockAdjustmentRequest;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.repository.ProductRepository;
import com.colaborapp.products.web.dto.ProductImportErrorResponse;
import com.colaborapp.products.web.dto.ProductImportResponse;
import com.colaborapp.security.AccessControlService;
import com.colaborapp.users.domain.RoleCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductStockReductionImportService {

    private static final String EXPECTED_HEADER = "codigo_barra,cantidad";

    private final ProductImportService productImportService;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CurrentTenantProvider currentTenantProvider;
    private final AccessControlService accessControlService;
    private final TransactionTemplate transactionTemplate;

    public ProductImportResponse importCsv(MultipartFile file) {
        accessControlService.requireAnyRole(RoleCode.ADMIN_SYSTEM, RoleCode.ADMIN_MARKET, RoleCode.COLLABORATOR);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona un archivo CSV para reducir stock.");
        }

        List<ProductImportErrorResponse> errors = new ArrayList<>();
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(productImportService.decodeCsv(file)))) {
            String header = stripUtf8Bom(reader.readLine());
            validateHeader(header);

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = productImportService.parseCsvLine(line);
                if (values.size() != 2) {
                    errors.add(new ProductImportErrorResponse(rowNumber, line, "La fila debe tener codigo_barra y cantidad."));
                    continue;
                }

                try {
                    String barcode = requiredValue(values.get(0), "Ingresa el codigo de barra.");
                    int quantity = parseQuantity(values.get(1));
                    Boolean adjusted = transactionTemplate.execute(status -> {
                        reduceStock(barcode, quantity);
                        return Boolean.TRUE;
                    });
                    if (Boolean.TRUE.equals(adjusted)) {
                        successCount++;
                    }
                } catch (Exception exception) {
                    errors.add(new ProductImportErrorResponse(rowNumber, line, normalizeMessage(exception)));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("No pudimos leer el archivo CSV. Revisa el formato e intenta nuevamente.");
        }

        return new ProductImportResponse(successCount, errors.size(), List.copyOf(errors));
    }

    private void reduceStock(String barcode, int quantity) {
        Long tenantId = currentTenantProvider.getCurrentTenant().getId();
        var product = productRepository.findByBarcodeForPos(tenantId, barcode.trim(), ProductStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("No encontramos un producto activo con ese codigo de barra."));
        requireProductMarketAccess(product.getStore());
        inventoryService.adjustStock(new StockAdjustmentRequest(product.getId(), -quantity, "REDUCCION_MASIVA_CSV"));
    }

    private void requireProductMarketAccess(com.colaborapp.stores.domain.Store store) {
        if (store != null && store.getMarket() != null && store.getMarket().getId() != null) {
            accessControlService.requireMarketAccess(store.getMarket().getId());
            return;
        }
        accessControlService.requireStoreAccess(store.getId());
    }

    private void validateHeader(String header) {
        if (header == null || !EXPECTED_HEADER.equalsIgnoreCase(header.strip())) {
            throw new BusinessException("La plantilla no coincide. Usa las columnas codigo_barra,cantidad.");
        }
    }

    private int parseQuantity(String rawValue) {
        try {
            int value = Integer.parseInt(requiredValue(rawValue, "Ingresa la cantidad a reducir.").trim());
            if (value <= 0) {
                throw new BusinessException("La cantidad a reducir debe ser mayor a 0.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new BusinessException("La cantidad debe ser un numero entero.");
        }
    }

    private String requiredValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String normalizeMessage(Exception exception) {
        Throwable candidate = exception;
        while (candidate.getCause() != null && !(candidate instanceof BusinessException)) {
            candidate = candidate.getCause();
        }
        return candidate.getMessage() == null ? "No pudimos procesar esta fila." : candidate.getMessage();
    }

    private String stripUtf8Bom(String value) {
        if (value == null || value.isEmpty() || value.charAt(0) != '\uFEFF') {
            return value;
        }
        return value.substring(1);
    }
}
