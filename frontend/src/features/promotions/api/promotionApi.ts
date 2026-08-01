import { apiFetch } from "@/shared/lib/api/client";

export type PromotionType =
  | "QUANTITY_BLOCK"
  | "HIGHEST_PRICE_BUNDLE"
  | "PERCENTAGE_DISCOUNT"
  | "MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT"
  | "PAYMENT_METHOD_DISCOUNT";

export type PromotionCampaign = {
  id: number;
  storeId: number;
  storeName: string;
  ownerUserId: number;
  ownerFullName: string;
  name: string;
  type: PromotionType;
  quantity: number | null;
  promotionalPrice: number | null;
  percentageDiscount: number | null;
  minimumPurchaseAmount: number | null;
  appliesToCash: boolean;
  appliesToDebit: boolean;
  appliesToCredit: boolean;
  appliesToTransfer: boolean;
  active: boolean;
  startsAt: string | null;
  endsAt: string | null;
  productCount: number;
  products: PromotionProduct[];
};

export type PromotionProduct = {
  productId: number;
  name: string;
  sku: string;
  stock: number;
};

export type PromotionCampaignInput = {
  ownerUserId?: number;
  name: string;
  type: PromotionType;
  quantity?: number;
  promotionalPrice?: number;
  percentageDiscount?: number;
  minimumPurchaseAmount?: number;
  appliesToCash?: boolean;
  appliesToDebit?: boolean;
  appliesToCredit?: boolean;
  appliesToTransfer?: boolean;
  startsAt?: string;
  endsAt?: string;
  active?: boolean;
};

export function listPromotions(params: { ownerUserId?: number } = {}) {
  const search = new URLSearchParams();
  if (params.ownerUserId) {
    search.set("ownerUserId", String(params.ownerUserId));
  }
  const suffix = search.toString() ? `?${search.toString()}` : "";
  return apiFetch<PromotionCampaign[]>(`/api/promotions${suffix}`);
}

export function createPromotion(input: PromotionCampaignInput) {
  return apiFetch<PromotionCampaign>("/api/promotions", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updatePromotion(promotionId: number, input: PromotionCampaignInput) {
  return apiFetch<PromotionCampaign>(`/api/promotions/${promotionId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function deletePromotion(promotionId: number) {
  return apiFetch<void>(`/api/promotions/${promotionId}`, {
    method: "DELETE",
  });
}

export function assignProductsToPromotion(promotionId: number, productIds: number[]) {
  return apiFetch<PromotionCampaign>(`/api/promotions/${promotionId}/products`, {
    method: "POST",
    body: JSON.stringify({ productIds }),
  });
}
