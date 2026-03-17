import { Navigate, Route, Routes } from "react-router-dom";

import { RequireAuth } from "@/features/auth/components/RequireAuth";
import { RequireRole } from "@/features/auth/components/RequireRole";
import { LoginPage } from "@/features/auth/pages/LoginPage";
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

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route path="/sales/today" element={<SalesTodayPage />} />
          <Route path="/stores" element={<Navigate to="/colaboradores" replace />} />

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM"]} />}>
            <Route path="/tiendas" element={<MarketsPage />} />
            <Route path="/closings" element={<DailyClosingPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_MARKET"]} />}>
            <Route path="/sales" element={<SalesPage />} />
            <Route path="/products" element={<ProductsPage />} />
          </Route>

          <Route element={<RequireRole allowedRoles={["ADMIN_SYSTEM", "ADMIN_MARKET"]} />}>
            <Route path="/colaboradores" element={<UsersPage />} />
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
