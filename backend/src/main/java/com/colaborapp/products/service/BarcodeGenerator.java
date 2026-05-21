package com.colaborapp.products.service;

import org.springframework.stereotype.Component;

import com.colaborapp.products.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BarcodeGenerator {
    private static final long MAX_SHORT_BARCODE = 9_999_999L;

    private final ProductRepository productRepository;

    public String generateUniqueBarcode() {
        while (true) {
            Long nextValue = productRepository.nextShortBarcodeSequenceValue();
            if (nextValue == null || nextValue <= 0) {
                throw new IllegalStateException("No pudimos reservar un codigo de producto.");
            }
            if (nextValue > MAX_SHORT_BARCODE) {
                throw new IllegalStateException("Se agotaron los codigos cortos disponibles para productos.");
            }

            String candidate = String.format("%07d", nextValue);
            if (!productRepository.existsByShortBarcode(candidate)) {
                return candidate;
            }
        }
    }
}
