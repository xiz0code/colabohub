import { Navigate, Route, Routes } from "react-router-dom";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { RequireRole } from "@/features/auth/components/RequireRole";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { useSession } from "@/features/auth/session/SessionProvider";
import { DashboardPage } from "@/features/dashboard/pages/DashboardPage";
import { MarketsPage } from "@/features/markets/pages/MarketsPage";
import { ProductsPage } from "@/features/products/pages/ProductsPage";
import { InventoryPage } from "@/features/inventory/pages/InventoryPage";
import { SalesPage } from "@/features/sales/pages/SalesPage";
import { CommissionsPage } from "@/features/commissions/pages/CommissionsPage";
import { DailyClosingPage } from "@/features/closings/pages/DailyClosingPage";
import { SalesTodayPage } from "@/features/reports/pages/SalesTodayPage";
import { UsersPage } from "@/features/users/pages/UsersPage";
import { AppShell } from "@/shared/components/layout/AppShell";
import { StoresPage } from "@/features/stores/pages/StoresPage";
import { PickupsPage } from "@/features/pickups/pages/PickupsPage";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<RoleHomeRedirect />} />
          <Route path="/stores" element={<Navigate to="/colaboradores" replace />} />
          <Route path="/legacy/users" element={<Navigate to="/colaboradores" replace />} />
          <Route path="/legacy/sellers" element={<Navigate to="/vendedores" replace />} />

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET", "COLLABORATOR", "STORE_USER"]} />}>
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["STORE_USER"]} />}>
            <Route path="/inventory" element={<InventoryPage />} />
            <Route path="/sales/today" element={<SalesTodayPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET"]} />}>
            <Route path="/reports/collaborators" element={<SalesTodayPage defaultTab="collaborators" />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM"]} />}>
            <Route path="/tiendas" element={<MarketsPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET"]} />}>
            <Route path="/closings" element={<DailyClosingPage />} />
            <Route path="/closings/monthly" element={<DailyClosingPage defaultMode="monthly" />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET", "SELLER"]} />}>
            <Route path="/sales" element={<SalesPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET", "SELLER", "STORE_USER"]} />}>
            <Route path="/pickups" element={<PickupsPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET", "STORE_USER", "SELLER"]} />}>
            <Route path="/products" element={<ProductsPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET"]} />}>
            <Route path="/colaboradores" element={<UsersPage mode="stores" />} />
            <Route path="/vendedores" element={<UsersPage mode="sellers" />} />
            <Route path="/commissions" element={<CommissionsPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET", "COLLABORATOR", "STORE_USER"]} />}>
            <Route path="/legacy/stores" element={<StoresPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}

function RoleHomeRedirect() {
  const { primaryRole } = useSession();
  return <Navigate to={primaryRole === "SELLER" ? "/sales" : "/dashboard"} replace />;
}
