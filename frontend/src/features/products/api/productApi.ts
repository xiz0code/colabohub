import { apiFetch, apiFetchBlob } from "@/shared/lib/api/client";
import type { PageResponse } from "@/shared/lib/api/types";

export type Product = {
  id: number;
  storeId: number;
  storeName: string;
  ownerUserId: number | null;
  ownerFullName: string | null;
  name: string;
  sku: string;
  description: string | null;
  salePrice: number;
  cost: number | null;
  stock: number;
  status: "ACTIVE" | "INACTIVE";
  barcode: string;
  hasPromotion: boolean;
  promotion: ProductPromotion | null;
  createdAt: string;
  updatedAt: string;
};

export type ProductPromotion =
  | {
      type: "QUANTITY_BLOCK";
      quantity: number;
      promotionalPrice: number;
      percentageDiscount: null;
    }
  | {
      type: "PERCENTAGE_DISCOUNT";
      quantity: null;
      promotionalPrice: null;
      percentageDiscount: number;
    };

export type ProductAuditLog = {
  id: number;
  fieldName: string;
  previousValue: string | null;
  newValue: string | null;
  createdAt: string;
  createdBy: string;
};

export type ListProductsParams = {
  page?: number;
  size?: number;
  storeId?: number;
  query?: string;
  status?: "ACTIVE" | "INACTIVE";
};

export type CreateProductInput = {
  storeId?: number;
  ownerUserId?: number;
  name: string;
  sku: string;
  description?: string;
  salePrice: number;
  cost?: number;
  initialStock: number;
  promotion?: ProductPromotionInput;
};

export type UpdateProductInput = {
  ownerUserId?: number;
  name: string;
  sku: string;
  description?: string;
  salePrice: number;
  cost?: number;
  stock: number;
  promotion?: ProductPromotionInput;
};

export type ProductPromotionInput = {
  type: "QUANTITY_BLOCK" | "PERCENTAGE_DISCOUNT";
  quantity?: number;
  promotionalPrice?: number;
  percentageDiscount?: number;
};

export type BarcodeLabelInput = {
  items: Array<{
    productId: number;
    quantity: number;
  }>;
  includeCollaboratorName: boolean;
};

export function listProducts(params: ListProductsParams = {}) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 10));
  if (params.query) {
    search.set("query", params.query);
  }
  if (params.storeId) {
    search.set("storeId", String(params.storeId));
  }
  if (params.status) {
    search.set("status", params.status);
  }

  return apiFetch<PageResponse<Product>>(`/api/products?${search.toString()}`);
}

export function createProduct(input: CreateProductInput) {
  return apiFetch<Product>("/api/products", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateProduct(productId: number, input: UpdateProductInput) {
  return apiFetch<Product>(`/api/products/${productId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function updateProductStatus(productId: number, status: Product["status"]) {
  return apiFetch<Product>(`/api/products/${productId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export function getProductAudit(productId: number) {
  return apiFetch<ProductAuditLog[]>(`/api/products/${productId}/audit`);
}

export function printBarcodeLabels(input: BarcodeLabelInput) {
  return apiFetchBlob("/api/products/barcode-labels", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
