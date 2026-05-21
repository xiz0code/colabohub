package com.colaborapp.pickups.web;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.pickups.domain.PickupStatus;
import com.colaborapp.pickups.service.PickupService;
import com.colaborapp.pickups.service.PickupLabelPdfService;
import com.colaborapp.pickups.web.dto.CreatePickupRequest;
import com.colaborapp.pickups.web.dto.PickupResponse;
import com.colaborapp.pickups.web.dto.PreparePickupCheckoutResponse;
import com.colaborapp.pickups.web.dto.ResolvePickupCheckoutRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/pickups")
@RequiredArgsConstructor
public class PickupController {

    private final PickupService pickupService;
    private final PickupLabelPdfService pickupLabelPdfService;

    @GetMapping
    public List<PickupResponse> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) PickupStatus status,
            @RequestParam(required = false) Long storeId) {
        return pickupService.list(query, status, storeId);
    }

    @PostMapping
    public PickupResponse create(@Valid @RequestBody CreatePickupRequest request) {
        return pickupService.create(
                request.storeId(),
                request.pickupNumber(),
                request.customerName(),
                request.description(),
                request.payable(),
                request.amountDue());
    }

    @PatchMapping("/{pickupId}/collect")
    public PickupResponse collect(@PathVariable Long pickupId) {
        return pickupService.markCollected(pickupId);
    }

    @PatchMapping("/{pickupId}/cancel")
    public PickupResponse cancel(@PathVariable Long pickupId) {
        return pickupService.cancel(pickupId);
    }

    @PostMapping("/{pickupId}/checkout")
    public PreparePickupCheckoutResponse checkout(@PathVariable Long pickupId) {
        return pickupService.prepareCheckout(pickupId);
    }

    @PostMapping("/checkout/resolve")
    public PreparePickupCheckoutResponse checkoutByCode(@Valid @RequestBody ResolvePickupCheckoutRequest request) {
        return pickupService.prepareCheckoutByCode(request.code());
    }

    @GetMapping(value = "/{pickupId}/label", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> label(@PathVariable Long pickupId) {
        PickupResponse pickup = pickupService.getPickupForLabel(pickupId);
        byte[] pdf = pickupLabelPdfService.generateLabel(pickup);
        String filename = "retiro-" + pickup.pickupNumber().replaceAll("[^a-zA-Z0-9-_]", "_") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
