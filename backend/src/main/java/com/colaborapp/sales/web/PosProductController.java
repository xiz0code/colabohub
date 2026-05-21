package com.colaborapp.sales.web;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.sales.service.PosProductLookupService;
import com.colaborapp.sales.web.dto.PosProductResponse;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/pos/products")
@RequiredArgsConstructor
public class PosProductController {

    private final PosProductLookupService posProductLookupService;

    @GetMapping("/scan/{barcode}")
    public PosProductResponse getByBarcode(@PathVariable @NotBlank String barcode) {
        return posProductLookupService.getByBarcode(barcode);
    }

    @GetMapping("/search")
    public List<PosProductResponse> search(
            @RequestParam("q") @NotBlank String query,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "30") int size) {
        return posProductLookupService.search(query, ownerUserId, size);
    }
}
