import { apiFetch } from "@/shared/lib/api/client";

export type Market = {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  contactName: string | null;
  description: string | null;
  city: string;
  currency: string;
  ufEnabled: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type UpsertMarketInput = {
  name: string;
  email: string;
  phone?: string;
  contactName?: string;
  description?: string;
  city?: string;
  currency?: string;
  ufEnabled?: boolean;
  active?: boolean;
};

export function listMarkets() {
  return apiFetch<Market[]>("/api/markets");
}

export function createMarket(input: UpsertMarketInput) {
  return apiFetch<Market>("/api/markets", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateMarket(marketId: number, input: UpsertMarketInput) {
  return apiFetch<Market>(`/api/markets/${marketId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function updateMarketStatus(marketId: number, active: boolean) {
  return apiFetch<Market>(`/api/markets/${marketId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ active }),
  });
}
