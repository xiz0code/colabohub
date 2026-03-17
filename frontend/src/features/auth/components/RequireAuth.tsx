import { Navigate, Outlet, useLocation } from "react-router-dom";

import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { useSession } from "@/features/auth/session/SessionProvider";

export function RequireAuth() {
  const location = useLocation();
  const { isAuthenticated, isLoading } = useSession();

  if (isLoading) {
    return <FeedbackMessage kind="info" message="Cargando sesion..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
