import { apiFetch } from "@/shared/lib/api/client";

export type GlobalFinancialSettings = {
  currentUfValue: number;
  ufLastUpdatedAt: string;
  useDynamicFixedCommission: boolean;
  commissionUfValue: number;
  commissionPercentageValue: number;
};

export type MarketFinancialSettings = {
  marketId: number;
  marketName: string;
  ufValue: number | null;
  ufUpdatedAt: string | null;
  ufManualOverride: boolean;
  useDynamicFixedCommission: boolean;
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

export function resetMarketUfValueToAutomatic() {
  return apiFetch<MarketFinancialSettings>("/api/settings/uf/auto", {
    method: "PUT",
  });
}

export function updateGlobalCommissionSettings(
  commissionUfValue: number,
  commissionPercentageValue: number,
  useDynamicFixedCommission: boolean,
) {
  return apiFetch<GlobalFinancialSettings>("/api/settings/global/commissions", {
    method: "PATCH",
    body: JSON.stringify({ commissionUfValue, commissionPercentageValue, useDynamicFixedCommission }),
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
