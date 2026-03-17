package com.colaborapp.products.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.colaborapp.products.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BarcodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductRepository productRepository;

    public String generateUniqueBarcode() {
        String barcode;
        do {
            barcode = generateCandidate();
        } while (productRepository.existsByBarcode(barcode));

        return barcode;
    }

    private String generateCandidate() {
        StringBuilder body = new StringBuilder("750");
        while (body.length() < 12) {
            body.append(RANDOM.nextInt(10));
        }

        int checksum = calculateEan13Checksum(body.toString());
        return body.append(checksum).toString();
    }

    private int calculateEan13Checksum(String body) {
        int sum = 0;
        for (int index = 0; index < body.length(); index++) {
            int digit = Character.getNumericValue(body.charAt(index));
            sum += (index % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
