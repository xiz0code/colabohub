import { API_BASE_URL, apiFetch } from "@/shared/lib/api/client";

export type CurrentUser = {
  id: number;
  email: string;
  fullName: string;
  active: boolean;
  roles: string[];
  activeMarketId: number | null;
  activeMarketName: string | null;
  marketIds: number[];
  storeIds: number[];
};

export function getCurrentUser() {
  return apiFetch<CurrentUser>("/api/me");
}

export function getGoogleLoginUrl() {
  return `${API_BASE_URL}/oauth2/authorization/google`;
}

export function getLogoutUrl() {
  return `${API_BASE_URL}/logout`;
}
