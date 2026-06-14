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

export type ClosingPaymentMethodSummary = {
  paymentMethod: "CASH" | "CREDIT" | "DEBITO" | "TRANSFER";
  saleCount: number;
  totalSalesAmount: number;
};

export type DailyClosing = {
  marketId: number;
  marketName: string;
  closingDate: string;
  saleCount: number;
  totalSalesAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  closedAt: string | null;
  closedBy: string | null;
  paymentMethods?: ClosingPaymentMethodSummary[];
  stores: DailyClosingStore[];
};

export type MonthlyClosingCollaborator = {
  collaboratorUserId: number;
  collaboratorName: string;
  collaboratorEmail: string;
  factura: boolean;
  saleCount: number;
  totalItems: number;
  totalSalesAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  totalIvaAmount: number;
  ivaToPayAmount: number;
};

export type MonthlyClosing = {
  marketId: number;
  marketName: string;
  closingMonth: string;
  saleCount: number;
  totalSalesAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  totalIvaAmount: number;
  totalIvaToPayAmount: number;
  closedAt: string | null;
  closedBy: string | null;
  paymentMethods?: ClosingPaymentMethodSummary[];
  collaborators: MonthlyClosingCollaborator[];
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

export function previewDailyClosing(marketId: number, date?: string) {
  const search = new URLSearchParams();
  if (date) {
    search.set("date", date);
  }

  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return apiFetch<DailyClosing>(`/api/markets/${marketId}/closings/daily/preview${suffix}`);
}

export function closeMonthly(marketId: number, month?: string) {
  const search = new URLSearchParams();
  if (month) {
    search.set("month", month);
  }

  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return apiFetch<MonthlyClosing>(`/api/markets/${marketId}/closings/monthly${suffix}`, {
    method: "POST",
  });
}

export function getMonthlyClosing(marketId: number, month: string) {
  return apiFetch<MonthlyClosing>(`/api/markets/${marketId}/closings/monthly/${month}`);
}

export function previewMonthlyClosing(marketId: number, month?: string) {
  const search = new URLSearchParams();
  if (month) {
    search.set("month", month);
  }

  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return apiFetch<MonthlyClosing>(`/api/markets/${marketId}/closings/monthly/preview${suffix}`);
}
