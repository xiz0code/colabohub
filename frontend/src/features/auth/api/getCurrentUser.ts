import { API_BASE_URL, apiFetch } from "@/shared/lib/api/client";

export type CurrentUser = {
  id: number;
  email: string;
  fullName: string;
  roles: string[];
  marketIds: number[];
  storeIds: number[];
  activeMarketName: string | null;
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
