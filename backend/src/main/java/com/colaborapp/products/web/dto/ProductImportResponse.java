package com.colaborapp.products.web.dto;

import java.util.List;

public record ProductImportResponse(
        int successCount,
        int errorCount,
        List<ProductImportErrorResponse> errors) {
}
