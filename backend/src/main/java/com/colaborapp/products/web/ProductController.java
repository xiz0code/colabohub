package com.colaborapp.products.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.colaborapp.common.web.dto.PageResponse;
import com.colaborapp.inventory.service.InventoryService;
import com.colaborapp.inventory.web.dto.StockMovementResponse;
import com.colaborapp.products.domain.ProductStatus;
import com.colaborapp.products.service.BarcodeLabelPdfService;
import com.colaborapp.products.service.ProductImportService;
import com.colaborapp.products.service.ProductStockReductionImportService;
import com.colaborapp.products.service.ProductService;
import com.colaborapp.products.web.dto.BarcodeLabelRequest;
import com.colaborapp.products.web.dto.ProductAuditLogResponse;
import com.colaborapp.products.web.dto.ProductCreateRequest;
import com.colaborapp.products.web.dto.ProductImportResponse;
import com.colaborapp.products.web.dto.ProductListQuery;
import com.colaborapp.products.web.dto.ProductPromotionGroupRequest;
import com.colaborapp.products.web.dto.ProductPromotionGroupResponse;
import com.colaborapp.products.web.dto.ProductResponse;
import com.colaborapp.products.web.dto.ProductStockIncreaseRequest;
import com.colaborapp.products.web.dto.ProductStatusUpdateRequest;
import com.colaborapp.products.web.dto.ProductUpdateRequest;
import com.colaborapp.products.web.dto.RecentBarcodeLabelProductResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final BarcodeLabelPdfService barcodeLabelPdfService;
    private final ProductImportService productImportService;
    private final ProductStockReductionImportService productStockReductionImportService;

    @GetMapping
    @PreAuthorize("@accessControl.canReadInventory()")
    public PageResponse<?> listProducts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.listProducts(new ProductListQuery(storeId, ownerUserId, query, status, page, size));
    }

    @GetMapping("/promotion-groups")
    @PreAuthorize("@accessControl.canReadInventory()")
    public List<ProductPromotionGroupResponse> listPromotionGroups(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long ownerUserId) {
        return productService.listPromotionGroups(storeId, ownerUserId);
    }

    @PostMapping("/promotion-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public ProductPromotionGroupResponse createPromotionGroup(@Valid @RequestBody ProductPromotionGroupRequest request) {
        return productService.createPromotionGroup(request);
    }

    @DeleteMapping("/promotion-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public void deletePromotionGroup(@PathVariable Long groupId) {
        productService.deletePromotionGroup(groupId);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("@accessControl.canReadInventory()")
    public Object getProduct(@PathVariable Long productId) {
        return productService.getProduct(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public ProductResponse createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return productService.createProduct(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessControl.canManageCatalog()")
    public ProductImportResponse importProducts(@RequestPart("file") MultipartFile file) {
        return productImportService.importCsv(file);
    }

    @PostMapping(value = "/stock-reductions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessControl.canManageInventory()")
    public ProductImportResponse importStockReductions(@RequestPart("file") MultipartFile file) {
        return productStockReductionImportService.importCsv(file);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("@accessControl.canManageCatalog()")
    public ProductResponse updateProduct(@PathVariable Long productId, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(productId, request);
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("@accessControl.canManageCatalog()")
    public ProductResponse updateStatus(@PathVariable Long productId, @Valid @RequestBody ProductStatusUpdateRequest request) {
        return productService.updateStatus(productId, request.status());
    }

    @PostMapping("/{productId}/stock/increase")
    @PreAuthorize("@accessControl.canAdjustCatalogStock()")
    public ProductResponse increaseStock(@PathVariable Long productId, @Valid @RequestBody ProductStockIncreaseRequest request) {
        return productService.increaseStock(productId, request.quantity());
    }

    @GetMapping("/{productId}/stock-movements")
    @PreAuthorize("@accessControl.canReadInventoryDetails()")
    public List<StockMovementResponse> getStockMovements(@PathVariable Long productId) {
        return inventoryService.getProductMovements(productId);
    }

    @GetMapping("/{productId}/audit")
    @PreAuthorize("@accessControl.canReadInventoryDetails()")
    public List<ProductAuditLogResponse> getProductAudit(@PathVariable Long productId) {
        return productService.getAuditTrail(productId);
    }

    @PostMapping("/barcode-labels")
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public ResponseEntity<byte[]> generateBarcodeLabels(@Valid @RequestBody BarcodeLabelRequest request) {
        byte[] pdf = barcodeLabelPdfService.generateLabels(productService.getProductsForBarcodeLabels(request), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=barcode-labels.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/barcode-labels/recent")
    @PreAuthorize("@accessControl.canManageOwnCatalog()")
    public List<RecentBarcodeLabelProductResponse> recentBarcodeLabelProducts(
            @RequestParam(defaultValue = "24") Integer hours) {
        return productService.getRecentProductsForBarcodeLabels(hours);
    }
}
