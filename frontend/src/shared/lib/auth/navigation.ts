import type { AppRole } from "@/shared/lib/auth/roles";

export type AppNavItem = {
  to: string;
  label: string;
};

const navItemsByRole: Record<AppRole, AppNavItem[]> = {
  ADMIN_SYSTEM: [
    { to: "/dashboard", label: "Dashboard" },
    { to: "/tiendas", label: "Espacios" },
    { to: "/colaboradores", label: "Tiendas" },
    { to: "/vendedores", label: "Vendedores" },
    { to: "/reports/collaborators", label: "Reportes" },
    { to: "/closings", label: "Cierres" },
    { to: "/sales", label: "Ventas" },
    { to: "/pickups", label: "Retiros" },
    { to: "/promotions", label: "Promociones" },
    { to: "/commissions", label: "Configuracion" },
  ],
  ADMIN_MARKET: [
    { to: "/dashboard", label: "Dashboard" },
    { to: "/colaboradores", label: "Tiendas" },
    { to: "/vendedores", label: "Vendedores" },
    { to: "/sales", label: "Ventas" },
    { to: "/pickups", label: "Retiros" },
    { to: "/promotions", label: "Promociones" },
    { to: "/products", label: "Stock" },
    { to: "/reports/collaborators", label: "Reportes" },
    { to: "/closings", label: "Cierres" },
    { to: "/commissions", label: "Configuracion" },
  ],
  SELLER: [
    { to: "/sales", label: "Ventas" },
    { to: "/pickups", label: "Retiros" },
    { to: "/products", label: "Stock" },
  ],
  STORE_USER: [
    { to: "/dashboard", label: "Dashboard" },
    { to: "/sales/today", label: "Mis ventas" },
    { to: "/pickups", label: "Retiros" },
    { to: "/promotions", label: "Promociones" },
    { to: "/products", label: "Mi stock" },
  ],
  COLLABORATOR: [
    { to: "/dashboard", label: "Dashboard" },
    { to: "/colaboradores", label: "Tiendas" },
    { to: "/sales", label: "Ventas" },
    { to: "/products", label: "Stock" },
  ],
};

export function getNavigationItems(primaryRole: AppRole | null): AppNavItem[] {
  if (!primaryRole) {
    return [];
  }
  return navItemsByRole[primaryRole];
}
