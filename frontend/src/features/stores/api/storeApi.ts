import { apiFetch } from "@/shared/lib/api/client";
import type { PageResponse } from "@/shared/lib/api/types";

export type Store = {
  id: number;
  marketId: number;
  marketName: string;
  code: string;
  name: string;
  type: "STOCK" | "PRIMARY" | "COLLABORATOR";
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type ListStoresParams = {
  page?: number;
  size?: number;
  query?: string;
  status?: "ACTIVE" | "INACTIVE";
};

export type UpsertStoreInput = {
  marketId: number;
  code: string;
  name: string;
  type: "PRIMARY" | "COLLABORATOR";
};

export function listStores(params: ListStoresParams = {}) {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 10));
  if (params.query) {
    search.set("query", params.query);
  }
  if (params.status) {
    search.set("status", params.status);
  }

  return apiFetch<PageResponse<Store>>(`/api/stores?${search.toString()}`);
}

export function createStore(input: UpsertStoreInput) {
  return apiFetch<Store>("/api/stores", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateStore(storeId: number, input: UpsertStoreInput) {
  return apiFetch<Store>(`/api/stores/${storeId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function updateStoreStatus(storeId: number, status: Store["status"]) {
  return apiFetch<Store>(`/api/stores/${storeId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}
