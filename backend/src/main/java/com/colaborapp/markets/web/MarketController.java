package com.colaborapp.markets.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.markets.service.MarketService;
import com.colaborapp.markets.web.dto.MarketRequest;
import com.colaborapp.markets.web.dto.MarketResponse;
import com.colaborapp.markets.web.dto.MarketStatusUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canManageMarkets()")
    public MarketResponse createMarket(@Valid @RequestBody MarketRequest request) {
        return marketService.createMarket(request);
    }

    @GetMapping
    public List<MarketResponse> listMarkets() {
        return marketService.listMarkets();
    }

    @GetMapping("/{marketId}")
    public MarketResponse getMarket(@PathVariable Long marketId) {
        return marketService.getMarket(marketId);
    }

    @PutMapping("/{marketId}")
    @PreAuthorize("@accessControl.canManageMarkets()")
    public MarketResponse updateMarket(@PathVariable Long marketId, @Valid @RequestBody MarketRequest request) {
        return marketService.updateMarket(marketId, request);
    }

    @PatchMapping("/{marketId}/status")
    @PreAuthorize("@accessControl.canManageMarkets()")
    public MarketResponse updateStatus(@PathVariable Long marketId, @Valid @RequestBody MarketStatusUpdateRequest request) {
        return marketService.updateStatus(marketId, request.active());
    }
}
