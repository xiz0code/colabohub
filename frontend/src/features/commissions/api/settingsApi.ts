import { apiFetch } from "@/shared/lib/api/client";

export type GlobalFinancialSettings = {
  currentUfValue: number;
  ufLastUpdatedAt: string;
  commissionUfValue: number;
  commissionPercentageValue: number;
};

export type MarketFinancialSettings = {
  marketId: number;
  marketName: string;
  ufValue: number | null;
  ufUpdatedAt: string | null;
  overrideEnabled: boolean;
  globalCommissionUfValue: number;
  globalCommissionPercentageValue: number;
  effectiveCommissionUfValue: number;
  effectiveCommissionPercentageValue: number;
  globalPromotionEnabled: boolean;
  globalPromotionPercentage: number | null;
};

export function getGlobalFinancialSettings() {
  return apiFetch<GlobalFinancialSettings>("/api/settings/global");
}

export function updateGlobalUfValue(ufValue: number) {
  return apiFetch<GlobalFinancialSettings>("/api/settings/global/uf", {
    method: "PATCH",
    body: JSON.stringify({ ufValue }),
  });
}

export function updateMarketUfValue(ufValue: number) {
  return apiFetch<MarketFinancialSettings>("/api/settings/uf", {
    method: "PUT",
    body: JSON.stringify({ ufValue }),
  });
}

export function updateGlobalCommissionSettings(commissionUfValue: number, commissionPercentageValue: number) {
  return apiFetch<GlobalFinancialSettings>("/api/settings/global/commissions", {
    method: "PATCH",
    body: JSON.stringify({ commissionUfValue, commissionPercentageValue }),
  });
}

export function getMarketFinancialSettings(marketId: number) {
  return apiFetch<MarketFinancialSettings>(`/api/settings/markets/${marketId}`);
}

export function updateMarketCommissionSettings(
  marketId: number,
  overrideEnabled: boolean,
  commissionUfValue: number,
  commissionPercentageValue: number,
  globalPromotionEnabled: boolean,
  globalPromotionPercentage: number | null,
) {
  return apiFetch<MarketFinancialSettings>(`/api/settings/markets/${marketId}/commissions`, {
    method: "PATCH",
    body: JSON.stringify({
      overrideEnabled,
      commissionUfValue,
      commissionPercentageValue,
      globalPromotionEnabled,
      globalPromotionPercentage,
    }),
  });
}
