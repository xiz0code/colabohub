package com.colaborapp.sales.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.sales.service.PosSaleService;
import com.colaborapp.sales.web.dto.CreatePosSaleRequest;
import com.colaborapp.sales.web.dto.PosPaymentMethodUpdateRequest;
import com.colaborapp.sales.web.dto.PosSaleItemRequest;
import com.colaborapp.sales.web.dto.PosSaleItemScanRequest;
import com.colaborapp.sales.web.dto.PosSaleItemUpdateRequest;
import com.colaborapp.sales.web.dto.PosSaleResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/pos/sales")
@RequiredArgsConstructor
@PreAuthorize("@accessControl.canOperatePos()")
public class PosSaleController {

    private final PosSaleService posSaleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PosSaleResponse createSale(@RequestBody(required = false) CreatePosSaleRequest request) {
        return posSaleService.createSale(request);
    }

    @GetMapping("/open")
    public PosSaleResponse getOpenSale(@RequestParam(required = false) Long marketId) {
        return posSaleService.getOpenSaleOrNull(marketId);
    }

    @GetMapping("/{saleId}")
    public PosSaleResponse getSale(@PathVariable Long saleId) {
        return posSaleService.getSale(saleId);
    }

    @PostMapping("/{saleId}/items")
    public PosSaleResponse addItem(@PathVariable Long saleId, @Valid @RequestBody PosSaleItemRequest request) {
        return posSaleService.addItem(saleId, request);
    }

    @PostMapping("/{saleId}/items/scan")
    public PosSaleResponse scanItem(@PathVariable Long saleId, @Valid @RequestBody PosSaleItemScanRequest request) {
        return posSaleService.scanItem(saleId, request);
    }

    @PatchMapping("/{saleId}/items/{itemId}")
    public PosSaleResponse updateItem(
            @PathVariable Long saleId,
            @PathVariable Long itemId,
            @Valid @RequestBody PosSaleItemUpdateRequest request) {
        return posSaleService.updateItem(saleId, itemId, request);
    }

    @DeleteMapping("/{saleId}/items/{itemId}")
    public PosSaleResponse removeItem(@PathVariable Long saleId, @PathVariable Long itemId) {
        return posSaleService.removeItem(saleId, itemId);
    }

    @PatchMapping("/{saleId}/payment-method")
    public PosSaleResponse updatePaymentMethod(
            @PathVariable Long saleId,
            @Valid @RequestBody PosPaymentMethodUpdateRequest request) {
        return posSaleService.updatePaymentMethod(saleId, request);
    }

    @PostMapping("/{saleId}/recalculate")
    public PosSaleResponse recalculate(@PathVariable Long saleId) {
        return posSaleService.recalculate(saleId);
    }

    @PostMapping("/{saleId}/confirm")
    public PosSaleResponse confirm(@PathVariable Long saleId) {
        return posSaleService.confirm(saleId);
    }

    @PostMapping("/{saleId}/cancel")
    public PosSaleResponse cancel(@PathVariable Long saleId) {
        return posSaleService.cancel(saleId);
    }
}
