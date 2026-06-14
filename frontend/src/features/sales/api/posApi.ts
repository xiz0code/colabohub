import { apiFetch } from "@/shared/lib/api/client";
import type { PageResponse } from "@/shared/lib/api/types";

export type PosSaleItem = {
  id: number;
  productId: number | null;
  manualEntry: boolean;
  manualReference: string | null;
  storeId: number;
  storeName: string;
  productName: string;
  collaboratorName: string | null;
  sku: string;
  barcode: string;
  quantity: number;
  availableStock?: number | null;
  baseUnitPrice: number;
  lineBaseSubtotal: number;
  promotionDiscountAmount: number;
  subtotal: number;
  pricingType: "NORMAL" | "PROMOTION";
  appliedPromotionId: number | null;
  appliedPromotionName: string | null;
  commission1Amount: number;
  commission2Amount: number;
  commissionIvaAmount: number;
  totalCommissionAmount: number;
  netAmount: number;
  promotionApplied: boolean;
  ufValue: number | null;
  commissionUfValue: number | null;
  commissionPercentageValue: number | null;
  totalCollaboratorAmount: number;
  totalClientAmount: number;
};

export type PosSaleStoreSummary = {
  storeId: number;
  storeName: string;
  lineCount: number;
  unitCount: number;
  subtotalAmount: number;
  commission1Amount: number;
  commission2Amount: number;
  commissionIvaAmount: number;
  totalCommissionAmount: number;
  netAmount: number;
};

export type PosProduct = {
  id: number;
  storeId: number;
  storeName: string;
  collaboratorName: string;
  name: string;
  sku: string;
  barcode: string;
  stock: number;
  salePrice: number;
};

export type PosSale = {
  id: number;
  saleNumber: string;
  marketId: number | null;
  status: "OPEN" | "CONFIRMED" | "CANCELLED";
  paymentMethod: "CASH" | "CREDIT" | "DEBITO" | "TRANSFER";
  netAmount: number;
  ivaAmount: number;
  subtotalAmount: number;
  totalDiscountAmount: number;
  totalAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  ufValue: number | null;
  commissionUfValue: number | null;
  commissionPercentageValue: number | null;
  openedAt: string;
  confirmedAt: string | null;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancellationReason: string | null;
  items: PosSaleItem[];
  storeSummaries: PosSaleStoreSummary[];
};

export type PosSaleSummary = {
  id: number;
  saleNumber: string;
  type: string;
  dateTime: string;
  status: "OPEN" | "CONFIRMED" | "CANCELLED";
  subtotalAmount: number;
  netAmount: number;
  ivaAmount: number;
  totalAmount: number;
  paymentMethod: PosSale["paymentMethod"] | null;
  marketId: number | null;
  sellerName: string | null;
};

export function createPosSale(paymentMethod?: PosSale["paymentMethod"]) {
  return apiFetch<PosSale>("/api/pos/sales", {
    method: "POST",
    body: JSON.stringify({
      ...(paymentMethod ? { paymentMethod } : {}),
    }),
  });
}

export type ListPosSalesParams = {
  page?: number;
  size?: number;
  marketId?: number | null;
  dateFrom?: string;
  dateTo?: string;
};

export function listPosSales(params: ListPosSalesParams = {}) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 25));
  if (params.dateFrom) {
    search.set("dateFrom", params.dateFrom);
  }
  if (params.dateTo) {
    search.set("dateTo", params.dateTo);
  }
  if (params.marketId) {
    search.set("marketId", String(params.marketId));
  }

  return apiFetch<PageResponse<PosSaleSummary>>(`/api/pos/sales?${search.toString()}`);
}

export function getOpenPosSale() {
  return apiFetch<PosSale>("/api/pos/sales/open");
}

export function getPosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}`);
}

export function editPosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/edit`, {
    method: "POST",
  });
}

export function addPosSaleItem(saleId: number, productId: number, quantity = 1) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/items`, {
    method: "POST",
    body: JSON.stringify({ productId, quantity }),
  });
}

export function addManualPosSaleItem(
  saleId: number,
  storeId: number,
  itemName: string,
  amount: number,
  description?: string,
  reference?: string,
  quantity = 1,
) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/manual-items`, {
    method: "POST",
    body: JSON.stringify({ storeId, itemName, amount, description, reference, quantity }),
  });
}

export function scanPosSaleItem(saleId: number, query: string, quantity = 1) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/items/scan`, {
    method: "POST",
    body: JSON.stringify({ query, quantity }),
  });
}

export function scanPosProduct(barcode: string) {
  return apiFetch<PosProduct>(`/api/pos/products/scan/${encodeURIComponent(barcode)}`);
}

export function searchPosProducts(query: string, options: { size?: number; ownerUserId?: number } = {}) {
  const search = new URLSearchParams();
  search.set("q", query);
  search.set("size", String(options.size ?? 50));
  if (options.ownerUserId) {
    search.set("ownerUserId", String(options.ownerUserId));
  }
  return apiFetch<PosProduct[]>(`/api/pos/products/search?${search.toString()}`);
}

export function updatePosSaleItem(saleId: number, itemId: number, quantity: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/items/${itemId}`, {
    method: "PATCH",
    body: JSON.stringify({ quantity }),
  });
}

export function removePosSaleItem(saleId: number, itemId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/items/${itemId}`, {
    method: "DELETE",
  });
}

export function updatePosPaymentMethod(saleId: number, paymentMethod: PosSale["paymentMethod"]) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/payment-method`, {
    method: "PATCH",
    body: JSON.stringify({ paymentMethod }),
  });
}

export function recalculatePosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/recalculate`, {
    method: "POST",
  });
}

export function confirmPosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/confirm`, {
    method: "POST",
  });
}

export function cancelPosSale(saleId: number, reason: string) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/cancel`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
