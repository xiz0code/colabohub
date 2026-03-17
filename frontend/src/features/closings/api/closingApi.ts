import { apiFetch } from "@/shared/lib/api/client";

export type DailyClosingStore = {
  storeId: number;
  storeName: string;
  saleCount: number;
  totalSalesAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  totalItems: number;
};

export type DailyClosing = {
  marketId: number;
  marketName: string;
  closingDate: string;
  saleCount: number;
  totalSalesAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  closedAt: string;
  closedBy: string;
  stores: DailyClosingStore[];
};

export function closeDaily(marketId: number, date?: string) {
  const search = new URLSearchParams();
  if (date) {
    search.set("date", date);
  }

  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return apiFetch<DailyClosing>(`/api/markets/${marketId}/closings/daily${suffix}`, {
    method: "POST",
  });
}

export function getDailyClosing(marketId: number, date: string) {
  return apiFetch<DailyClosing>(`/api/markets/${marketId}/closings/${date}`);
}
