import { apiFetch } from "@/shared/lib/api/client";

export type UserRole = "ADMIN_SYSTEM" | "ADMIN_MARKET" | "COLLABORATOR" | "STORE_USER";

export type AppUser = {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  contactName: string | null;
  description: string | null;
  monthlyRent: number | null;
  startDate: string | null;
  standNumber: string | null;
  roles: string[];
  marketIds: number[];
  storeIds: number[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type UpsertUserInput = {
  email: string;
  fullName: string;
  phone?: string;
  contactName?: string;
  description?: string;
  monthlyRent?: number;
  startDate?: string;
  standNumber?: string;
  role: UserRole;
  marketIds?: number[];
  storeIds?: number[];
  active?: boolean;
};

export function listUsers() {
  return apiFetch<AppUser[]>("/api/users");
}

export function createUser(input: UpsertUserInput) {
  return apiFetch<AppUser>("/api/users", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function updateUser(userId: number, input: UpsertUserInput) {
  return apiFetch<AppUser>(`/api/users/${userId}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function updateUserStatus(userId: number, active: boolean) {
  return apiFetch<AppUser>(`/api/users/${userId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ active }),
  });
}
