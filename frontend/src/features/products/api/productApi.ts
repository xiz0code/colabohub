import { apiFetch, apiFetchBlob } from "@/shared/lib/api/client";
import type { PageResponse } from "@/shared/lib/api/types";

export type Product = {
  id: number;
  storeId?: number;
  storeName?: string;
  ownerUserId?: number | null;
  ownerFullName?: string | null;
  promotionGroupId?: number | null;
  promotionGroupName?: string | null;
  name: string;
  sku: string;
  description?: string | null;
  salePrice: number;
  cost?: number | null;
  stock: number;
  status?: "ACTIVE" | "INACTIVE";
  barcode?: string;
  hasPromotion?: boolean;
  promotion?: ProductPromotion | null;
  createdAt?: string;
  updatedAt?: string;
  available?: boolean;
  price?: number;
};

type RawProduct = Product & {
  price?: number;
};

export type ProductPromotion =
  | {
      type: "QUANTITY_BLOCK";
      quantity: number;
      promotionalPrice: number;
      percentageDiscount: null;
      appliesToCash?: boolean | null;
      appliesToDebit?: boolean | null;
      endsAt?: string | null;
    }
  | {
      type: "PERCENTAGE_DISCOUNT";
      quantity: null;
      promotionalPrice: null;
      percentageDiscount: number;
      appliesToCash?: boolean | null;
      appliesToDebit?: boolean | null;
      endsAt?: string | null;
    }
  | {
      type: "PAYMENT_METHOD_DISCOUNT";
      quantity: null;
      promotionalPrice: null;
      percentageDiscount: number;
      appliesToCash: boolean;
      appliesToDebit: boolean;
      endsAt?: string | null;
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
  ownerUserId?: number;
  query?: string;
  status?: "ACTIVE" | "INACTIVE";
};

export type CreateProductInput = {
  storeId?: number;
  ownerUserId?: number;
  promotionGroupId?: number;
  promotionGroupName?: string;
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
  promotionGroupId?: number;
  promotionGroupName?: string;
  name: string;
  sku: string;
  description?: string;
  salePrice: number;
  cost?: number;
  stock: number;
  promotion?: ProductPromotionInput;
};

export type ProductPromotionInput = {
  type: "QUANTITY_BLOCK" | "PERCENTAGE_DISCOUNT" | "PAYMENT_METHOD_DISCOUNT";
  quantity?: number;
  promotionalPrice?: number;
  percentageDiscount?: number;
  appliesToCash?: boolean;
  appliesToDebit?: boolean;
  endsAt?: string;
};

export type ProductPromotionGroup = {
  id: number;
  storeId: number;
  ownerUserId: number;
  ownerFullName: string;
  name: string;
};

export type BarcodeLabelInput = {
  items: Array<{
    productId: number;
    quantity: number;
  }>;
  includeCollaboratorName: boolean;
};

export type ProductImportResult = {
  successCount: number;
  errorCount: number;
  errors: Array<{
    rowNumber: number;
    rowData: string;
    message: string;
  }>;
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
  if (params.ownerUserId) {
    search.set("ownerUserId", String(params.ownerUserId));
  }
  if (params.status) {
    search.set("status", params.status);
  }

  return apiFetch<PageResponse<RawProduct>>(`/api/products?${search.toString()}`).then((page) => ({
    ...page,
    content: page.content.map(normalizeProduct),
  }));
}

export function createProduct(input: CreateProductInput) {
  return apiFetch<RawProduct>("/api/products", {
    method: "POST",
    body: JSON.stringify(input),
  }).then(normalizeProduct);
}

export function updateProduct(productId: number, input: UpdateProductInput) {
  return apiFetch<RawProduct>(`/api/products/${productId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  }).then(normalizeProduct);
}

export function updateProductStatus(productId: number, status: Product["status"]) {
  return apiFetch<RawProduct>(`/api/products/${productId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  }).then(normalizeProduct);
}

export function listPromotionGroups(params: { storeId?: number; ownerUserId?: number } = {}) {
  const search = new URLSearchParams();
  if (params.storeId) {
    search.set("storeId", String(params.storeId));
  }
  if (params.ownerUserId) {
    search.set("ownerUserId", String(params.ownerUserId));
  }
  const suffix = search.toString() ? `?${search.toString()}` : "";
  return apiFetch<ProductPromotionGroup[]>(`/api/products/promotion-groups${suffix}`);
}

export function createPromotionGroup(input: { storeId?: number; ownerUserId?: number; name: string }) {
  return apiFetch<ProductPromotionGroup>("/api/products/promotion-groups", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deletePromotionGroup(groupId: number) {
  return apiFetch<void>(`/api/products/promotion-groups/${groupId}`, {
    method: "DELETE",
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

export function importProductsCsv(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return apiFetch<ProductImportResult>("/api/products/import", {
    method: "POST",
    body: formData,
  });
}

export function importStockReductionsCsv(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return apiFetch<ProductImportResult>("/api/products/stock-reductions/import", {
    method: "POST",
    body: formData,
  });
}

function normalizeProduct(product: RawProduct): Product {
  const salePrice = typeof product.salePrice === "number" ? product.salePrice : typeof product.price === "number" ? product.price : 0;

  return {
    ...product,
    salePrice,
  };
}
