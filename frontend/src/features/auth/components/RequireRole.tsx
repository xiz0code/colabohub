import { Navigate, Outlet } from "react-router-dom";

import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { useSession } from "@/features/auth/session/SessionProvider";

type RequireRoleProps = {
  allowedRoles: string[];
};

export function RequireRole({ allowedRoles }: RequireRoleProps) {
  const { isAuthenticated, isLoading, roles, primaryRole } = useSession();

  if (isLoading) {
    return <FeedbackMessage kind="info" message="Validando permisos..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const isAllowed = roles.some((role) => allowedRoles.includes(role));
  if (!isAllowed) {
    return <Navigate to={primaryRole === "SELLER" ? "/sales" : "/dashboard"} replace />;
  }

  return <Outlet />;
}
