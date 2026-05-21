import { apiFetch, apiFetchBlob } from "@/shared/lib/api/client";
import type { PosSale } from "@/features/sales/api/posApi";

export type PickupStatus = "PENDING" | "CHECKOUT_IN_PROGRESS" | "COLLECTED" | "CANCELLED";

export type Pickup = {
  id: number;
  marketId: number;
  marketName: string;
  storeId: number;
  storeName: string;
  pickupBarcode: string;
  pickupNumber: string;
  customerName: string;
  description: string;
  payable: boolean;
  amountDue: number | null;
  status: PickupStatus;
  linkedSaleId: number | null;
  collectedAt: string | null;
  collectedBy: string | null;
  createdAt: string;
};

export type PreparePickupCheckoutResponse = {
  pickup: Pickup;
  sale: PosSale;
};

export function listPickups(params?: { query?: string; status?: PickupStatus | "ALL"; storeId?: number | null }) {
  const searchParams = new URLSearchParams();
  if (params?.query?.trim()) {
    searchParams.set("query", params.query.trim());
  }
  if (params?.status && params.status !== "ALL") {
    searchParams.set("status", params.status);
  }
  if (params?.storeId) {
    searchParams.set("storeId", String(params.storeId));
  }
  const suffix = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  return apiFetch<Pickup[]>(`/api/pickups${suffix}`);
}

export function createPickup(input: {
  storeId?: number;
  pickupNumber: string;
  customerName: string;
  description: string;
  payable: boolean;
  amountDue?: number;
}) {
  return apiFetch<Pickup>("/api/pickups", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function collectPickup(pickupId: number) {
  return apiFetch<Pickup>(`/api/pickups/${pickupId}/collect`, {
    method: "PATCH",
  });
}

export function cancelPickup(pickupId: number) {
  return apiFetch<Pickup>(`/api/pickups/${pickupId}/cancel`, {
    method: "PATCH",
  });
}

export function checkoutPickup(pickupId: number) {
  return apiFetch<PreparePickupCheckoutResponse>(`/api/pickups/${pickupId}/checkout`, {
    method: "POST",
  });
}

export function checkoutPickupByCode(code: string) {
  return apiFetch<PreparePickupCheckoutResponse>("/api/pickups/checkout/resolve", {
    method: "POST",
    body: JSON.stringify({ code }),
  });
}

export async function downloadPickupLabel(pickupId: number) {
  return apiFetchBlob(`/api/pickups/${pickupId}/label`, {
    method: "GET",
  });
}
