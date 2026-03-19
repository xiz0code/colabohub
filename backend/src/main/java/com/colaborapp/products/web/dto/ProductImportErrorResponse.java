package com.colaborapp.products.web.dto;

public record ProductImportErrorResponse(
        int rowNumber,
        String rowData,
        String message) {
}
