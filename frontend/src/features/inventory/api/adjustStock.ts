import { apiFetch } from "@/shared/lib/api/client";

export type AdjustStockInput = {
  productId: number;
  quantityDelta: number;
  reason: string;
};

export function adjustStock(input: AdjustStockInput) {
  return apiFetch("/api/inventory/adjustments", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
