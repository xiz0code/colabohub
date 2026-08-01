import { apiFetch } from "@/shared/lib/api/client";

export type ReportStoreSummary = {
  storeId: number;
  storeName: string;
  salesCount?: number;
  totalSales?: number;
  subtotalAmount?: number;
  totalAmount?: number;
  totalCommissionAmount?: number;
  totalCommission?: number;
  netAmount?: number;
  totalNet?: number;
};

export type SalesTodaySummary = {
  businessDate: string;
  totalSales: number;
  totalAmount: number;
  totalCommission: number;
  totalNet: number;
  salesCount: number;
  stores: ReportStoreSummary[];
};

export type SaleTodayStoreBreakdown = {
  storeId: number;
  storeName: string;
  lineCount: number;
  unitCount: number;
  subtotalAmount: number;
  totalCommissionAmount: number;
  netAmount: number;
};

export type SaleTodayDetail = {
  saleId: number;
  saleNumber: string;
  confirmedAt: string;
  totalAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  stores: SaleTodayStoreBreakdown[];
  items: SaleTodayItem[];
};

export type SalesTodayDetails = SalesTodaySummary & {
  sales: SaleTodayDetail[];
};

export type SaleTodayItem = {
  itemId: number;
  productName: string;
  collaboratorName: string | null;
  storeName: string;
  quantity: number;
  subtotalAmount?: number;
  subtotal?: number;
  totalCommissionAmount: number;
  netAmount: number;
};

export type DashboardSummary = {
  businessDate: string;
  salesCount: number;
  totalAmount: number;
  totalCommission: number;
  totalNet: number;
  activeProducts: number;
  lowStockProducts: number;
  pendingPickups: number;
  previousDaySalesCount: number;
  previousDayAmount: number;
  salesChangePercentage: number;
  trend: Array<{
    date: string;
    salesCount: number;
    totalAmount: number;
    totalNet: number;
  }>;
  paymentMethods: Array<{
    paymentMethod: string;
    salesCount: number;
    totalAmount: number;
  }>;
  stores: ReportStoreSummary[];
};

export type CollaboratorSaleEntry = {
  saleId: number;
  saleNumber: string;
  confirmedAt: string;
  paymentMethod: string | null;
  productName: string;
  promotionLabel: string;
  quantity: number;
  unitPrice: number;
  ufValue: number | null;
  fixedCommissionAmount: number;
  variableCommissionAmount: number;
  commissionIvaAmount: number;
  totalAmount: number;
  commissionAmount: number;
  netAmount: number;
};

export type CollaboratorSalesReport = {
  collaboratorUserId: number;
  collaboratorName: string;
  dateFrom: string;
  dateTo: string;
  totalAmount: number;
  totalCommissionAmount: number;
  totalNetAmount: number;
  totalIvaAmount: number;
  entries: CollaboratorSaleEntry[];
};

export type CommissionRecalculationResult = {
  dateFrom: string;
  dateTo: string;
  reviewedSales: number;
  recalculatedSales: number;
  ufDatesUsed: number;
};

export function getDashboardSummary() {
  return apiFetch<DashboardSummary>("/api/reports/sales/dashboard");
}

export function getSalesTodaySummary() {
  return apiFetch<SalesTodaySummary>("/api/reports/sales/today");
}

export function getSalesTodayDetails() {
  return apiFetch<SalesTodayDetails>("/api/reports/sales/today/details");
}

export function getCollaboratorSalesReport(collaboratorUserId: number, dateFrom?: string, dateTo?: string) {
  const search = new URLSearchParams();
  if (dateFrom) {
    search.set("dateFrom", dateFrom);
  }
  if (dateTo) {
    search.set("dateTo", dateTo);
  }

  const suffix = search.size > 0 ? `?${search.toString()}` : "";
  return apiFetch<CollaboratorSalesReport>(`/api/reports/sales/collaborator/${collaboratorUserId}${suffix}`);
}

export function recalculateSalesCommissions(dateFrom: string, dateTo: string) {
  return apiFetch<CommissionRecalculationResult>("/api/reports/sales/commissions/recalculate", {
    method: "POST",
    body: JSON.stringify({ dateFrom, dateTo }),
  });
}
