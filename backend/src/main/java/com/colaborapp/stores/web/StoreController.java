package com.colaborapp.stores.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.stores.domain.StoreStatus;
import com.colaborapp.stores.service.StoreService;
import com.colaborapp.stores.web.dto.StoreListQuery;
import com.colaborapp.stores.web.dto.StoreRequest;
import com.colaborapp.stores.web.dto.StoreResponse;
import com.colaborapp.stores.web.dto.StoreStatusUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
@PreAuthorize("@accessControl.canManageCatalog()")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public PageResponse<StoreResponse> listStores(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String query,
            @org.springframework.web.bind.annotation.RequestParam(required = false) StoreStatus status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return storeService.listStores(new StoreListQuery(query, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse createStore(@Valid @RequestBody StoreRequest request) {
        return storeService.createStore(request);
    }

    @PutMapping("/{storeId}")
    public StoreResponse updateStore(@PathVariable Long storeId, @Valid @RequestBody StoreRequest request) {
        return storeService.updateStore(storeId, request);
    }

    @PatchMapping("/{storeId}/status")
    public StoreResponse updateStatus(@PathVariable Long storeId, @Valid @RequestBody StoreStatusUpdateRequest request) {
        return storeService.updateStatus(storeId, request.status());
    }
}
