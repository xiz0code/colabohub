import { apiFetch } from "@/shared/lib/api/client";

export type StockMovement = {
  id: number;
  productId: number;
  storeId: number;
  storeName: string;
  type: string;
  quantity: number;
  previousStock: number;
  newStock: number;
  referenceType: string;
  referenceId: number | null;
  createdAt: string;
  createdBy: string;
};

export function listStockMovements(productId: number) {
  return apiFetch<StockMovement[]>(`/api/products/${productId}/stock-movements`);
}
