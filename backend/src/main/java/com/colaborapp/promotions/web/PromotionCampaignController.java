package com.colaborapp.promotions.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.colaborapp.promotions.service.PromotionCampaignService;
import com.colaborapp.promotions.web.dto.PromotionCampaignRequest;
import com.colaborapp.promotions.web.dto.PromotionCampaignResponse;
import com.colaborapp.promotions.web.dto.PromotionProductAssignmentRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionCampaignController {

    private final PromotionCampaignService promotionCampaignService;

    @GetMapping
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public List<PromotionCampaignResponse> listPromotions(@RequestParam(required = false) Long ownerUserId) {
        return promotionCampaignService.listPromotions(ownerUserId);
    }

    @GetMapping("/{promotionId}")
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public PromotionCampaignResponse getPromotion(@PathVariable Long promotionId) {
        return promotionCampaignService.getPromotion(promotionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public PromotionCampaignResponse createPromotion(@Valid @RequestBody PromotionCampaignRequest request) {
        return promotionCampaignService.createPromotion(request);
    }

    @PutMapping("/{promotionId}")
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public PromotionCampaignResponse updatePromotion(
            @PathVariable Long promotionId,
            @Valid @RequestBody PromotionCampaignRequest request) {
        return promotionCampaignService.updatePromotion(promotionId, request);
    }

    @DeleteMapping("/{promotionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public void deletePromotion(@PathVariable Long promotionId) {
        promotionCampaignService.deletePromotion(promotionId);
    }

    @PostMapping("/{promotionId}/products")
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public PromotionCampaignResponse assignProducts(
            @PathVariable Long promotionId,
            @Valid @RequestBody PromotionProductAssignmentRequest request) {
        return promotionCampaignService.assignProducts(promotionId, request.productIds());
    }
}
