import { apiFetch } from "@/shared/lib/api/client";

export type PosSaleItem = {
  id: number;
  productId: number;
  storeId: number;
  storeName: string;
  productName: string;
  collaboratorName: string | null;
  sku: string;
  barcode: string;
  quantity: number;
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
  name: string;
  sku: string;
  barcode: string;
  stock: number;
  salePrice: number;
};

export type PosSale = {
  id: number;
  saleNumber: string;
  status: "OPEN" | "CONFIRMED" | "CANCELLED";
  paymentMethod: "CASH" | "CREDIT" | "DEBITO" | "TRANSFER";
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
  items: PosSaleItem[];
  storeSummaries: PosSaleStoreSummary[];
};

export function createPosSale(paymentMethod?: PosSale["paymentMethod"]) {
  return apiFetch<PosSale>("/api/pos/sales", {
    method: "POST",
    body: JSON.stringify({
      ...(paymentMethod ? { paymentMethod } : {}),
    }),
  });
}

export function getOpenPosSale() {
  return apiFetch<PosSale | null>("/api/pos/sales/open");
}

export function getPosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}`);
}

export function addPosSaleItem(saleId: number, productId: number, quantity = 1) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/items`, {
    method: "POST",
    body: JSON.stringify({ productId, quantity }),
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

export function searchPosProducts(query: string) {
  return apiFetch<PosProduct[]>(`/api/pos/products/search?q=${encodeURIComponent(query)}`);
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

export function cancelPosSale(saleId: number) {
  return apiFetch<PosSale>(`/api/pos/sales/${saleId}/cancel`, {
    method: "POST",
  });
}
