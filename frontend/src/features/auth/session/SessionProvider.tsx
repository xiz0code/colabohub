import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { getCurrentUser, getGoogleLoginUrl, getLogoutUrl, type CurrentUser } from "@/features/auth/api/getCurrentUser";
import { ApiError } from "@/shared/lib/api/client";
import { getPrimaryRole, getRoleLabel, type AppRole } from "@/shared/lib/auth/roles";

type SessionContextValue = {
  user: CurrentUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  roles: string[];
  primaryRole: AppRole | null;
  visibleRoleLabel: string;
  loginUrl: string;
  logoutUrl: string;
};

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

export function SessionProvider({ children }: PropsWithChildren) {
  const currentUserQuery = useQuery({
    queryKey: ["auth", "me"],
    queryFn: async () => {
      try {
        return await getCurrentUser();
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          return null;
        }
        throw error;
      }
    },
    retry: false,
  });
  const [stableUser, setStableUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    if (currentUserQuery.data) {
      setStableUser(currentUserQuery.data);
      return;
    }

    if (currentUserQuery.data === null && !currentUserQuery.isLoading) {
      setStableUser(null);
    }
  }, [currentUserQuery.data, currentUserQuery.isLoading]);

  const value = useMemo<SessionContextValue>(() => {
    const user = currentUserQuery.data ?? (currentUserQuery.isLoading ? stableUser : null);
    const roles = user?.roles ?? [];
    const primaryRole = getPrimaryRole(roles);
    return {
      user,
      isAuthenticated: Boolean(user),
      isLoading: currentUserQuery.isLoading,
      roles,
      primaryRole,
      visibleRoleLabel: getRoleLabel(primaryRole),
      loginUrl: getGoogleLoginUrl(),
      logoutUrl: getLogoutUrl(),
    };
  }, [currentUserQuery.data, currentUserQuery.isLoading, stableUser]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession must be used within SessionProvider.");
  }
  return context;
}
