package com.colaborapp.sales.service;

import java.time.Year;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.colaborapp.sales.repository.SaleRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SaleNumberGenerator {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^S-\\d{4}-\\d{8}$");

    private final SaleRepository saleRepository;

    public String next() {
        int year = Year.now().getValue();
        String prefix = "S-" + year + "-";
        long nextSequence = saleRepository.findFirstBySaleNumberStartingWithOrderBySaleNumberDesc(prefix)
                .map(sale -> sale.getSaleNumber())
                .filter(saleNumber -> PREFIX_PATTERN.matcher(saleNumber).matches())
                .map(saleNumber -> saleNumber.substring(prefix.length()))
                .map(Long::parseLong)
                .map(sequence -> sequence + 1)
                .orElse(1L);
        return prefix + "%08d".formatted(nextSequence);
    }
}
