import { MemoryRouter, Route, Routes } from "react-router-dom";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AppShell } from "@/shared/components/layout/AppShell";

const sessionMock = vi.fn();
const openPosMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => sessionMock(),
}));

vi.mock("@/features/sales/components/PosLauncherProvider", () => ({
  PosLauncherProvider: ({ children }: { children: unknown }) => <>{children}</>,
  usePosLauncher: () => ({
    openPos: openPosMock,
    canOperatePos: true,
    isOpening: false,
  }),
}));

beforeEach(() => {
  sessionMock.mockReset();
  openPosMock.mockReset();
});

afterEach(() => {
  cleanup();
});

describe("AppShell", () => {
  it("keeps Administrador General navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Admin", marketIds: [], storeIds: [], email: "admin@example.com", active: true, activeMarketId: null, activeMarketName: null },
      isLoading: false,
      roles: ["ADMIN_SYSTEM"],
      primaryRole: "ADMIN_SYSTEM",
      visibleRoleLabel: "Administrador General",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Espacios")).toBeInTheDocument();
    expect(screen.getByText("Reportes")).toBeInTheDocument();
    expect(screen.getByText("Acceso global")).toBeInTheDocument();
    expect(screen.getByText("Gestionar Espacios")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Espacios" }));

    expect(screen.getByText("Espacios")).toBeInTheDocument();
    expect(screen.getByText("Tiendas")).toBeInTheDocument();
    expect(screen.getByText("Vendedores")).toBeInTheDocument();
    expect(screen.getByText("Configuracion")).toBeInTheDocument();
    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Retiros")).toBeInTheDocument();
  });

  it("keeps Administrador de Espacio navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Admin Tienda", marketIds: [2], storeIds: [], email: "admin.tienda@example.com", active: true, activeMarketId: 2, activeMarketName: "Sakura Store" },
      isLoading: false,
      roles: ["ADMIN_MARKET"],
      primaryRole: "ADMIN_MARKET",
      visibleRoleLabel: "Administrador de Espacio",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Retiros")).toBeInTheDocument();
    expect(screen.getByText("Stock")).toBeInTheDocument();
    expect(screen.getByText("Reportes")).toBeInTheDocument();
    expect(screen.getByText("Cierres")).toBeInTheDocument();
    expect(screen.getByText("Nueva venta")).toBeInTheDocument();
    expect(screen.getByText("Sakura Store")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Stock" }));

    expect(screen.getByText("Tiendas")).toBeInTheDocument();
    expect(screen.getByText("Vendedores")).toBeInTheDocument();
    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Reportes")).toBeInTheDocument();
    expect(screen.getByText("Cierres")).toBeInTheDocument();
    expect(screen.getByText("Configuracion")).toBeInTheDocument();
    expect(screen.queryByText("Espacios")).not.toBeInTheDocument();
  });

  it("abre la caja global desde el header sin cambiar de pantalla", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Admin Tienda", marketIds: [2], storeIds: [], email: "admin.tienda@example.com", active: true, activeMarketId: 2, activeMarketName: "Sakura Store" },
      isLoading: false,
      roles: ["ADMIN_MARKET"],
      primaryRole: "ADMIN_MARKET",
      visibleRoleLabel: "Administrador de Espacio",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    expect(openPosMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Dashboard page")).toBeInTheDocument();
  });

  it("shows the role label once and prioritizes the active market name in the header", () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Karina Gonzalez", marketIds: [2], storeIds: [], email: "karina@example.com", active: true, activeMarketId: 2, activeMarketName: "Fast And Near" },
      isLoading: false,
      roles: ["ADMIN_MARKET"],
      primaryRole: "ADMIN_MARKET",
      visibleRoleLabel: "Administrador de Espacio",
      logoutUrl: "/logout",
    });

    renderShell("/dashboard");

    expect(screen.getByText("Fast And Near")).toBeInTheDocument();
    expect(screen.getByText("Karina Gonzalez")).toBeInTheDocument();
    expect(screen.getAllByText("Administrador de Espacio")).toHaveLength(1);
    expect(screen.getByText(/Powered by Xizo Dev's/i)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Xizo Dev's" })).not.toBeInTheDocument();
  });

  it("keeps collaborator navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Tienda Demo", marketIds: [3], storeIds: [8], email: "colab@example.com", active: true, activeMarketId: 3, activeMarketName: "Sakura Store" },
      isLoading: false,
      roles: ["STORE_USER"],
      primaryRole: "STORE_USER",
      visibleRoleLabel: "Tienda",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Mis ventas")).toBeInTheDocument();
    expect(screen.getByText("Mi stock")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Mi stock" }));

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Mis ventas")).toBeInTheDocument();
    expect(screen.getByText("Retiros")).toBeInTheDocument();
    expect(screen.getByText("Mi stock")).toBeInTheDocument();
    expect(screen.getByText("Stock page")).toBeInTheDocument();
    expect(screen.queryByText("Nueva venta")).not.toBeInTheDocument();
    expect(screen.queryByText("Espacios")).not.toBeInTheDocument();
  });

  it("shows seller navigation limited to ventas y stock", () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Vendedor Demo", marketIds: [3], storeIds: [], email: "seller@example.com", active: true, activeMarketId: 3, activeMarketName: "Sakura Store" },
      isLoading: false,
      roles: ["SELLER"],
      primaryRole: "SELLER",
      visibleRoleLabel: "Vendedor",
      logoutUrl: "/logout",
    });

    renderShell("/sales");

    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Retiros")).toBeInTheDocument();
    expect(screen.getByText("Stock")).toBeInTheDocument();
    expect(screen.getByText("Nueva venta")).toBeInTheDocument();
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("Reportes")).not.toBeInTheDocument();
    expect(screen.queryByText("Configuracion")).not.toBeInTheDocument();
  });

  it("shows stable loading placeholder instead of collapsing the menu", () => {
    sessionMock.mockReturnValue({
      user: null,
      isLoading: true,
      roles: [],
      primaryRole: null,
      visibleRoleLabel: "Usuario",
      logoutUrl: "/logout",
    });

    renderShell("/dashboard");

    expect(screen.getByLabelText("Navegacion principal")).toBeInTheDocument();
    expect(screen.queryByText("Espacios")).not.toBeInTheDocument();
    expect(screen.queryByText("Ventas")).not.toBeInTheDocument();
  });
});

function renderShell(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<div>Dashboard page</div>} />
          <Route path="/tiendas" element={<div>Espacios page</div>} />
          <Route path="/colaboradores" element={<div>Tiendas page</div>} />
          <Route path="/vendedores" element={<div>Vendedores page</div>} />
          <Route path="/sales" element={<div>Ventas page</div>} />
          <Route path="/sales/today" element={<div>Mis ventas page</div>} />
          <Route path="/reports/collaborators" element={<div>Reportes tiendas page</div>} />
          <Route path="/pickups" element={<div>Retiros page</div>} />
          <Route path="/products" element={<div>Stock page</div>} />
          <Route path="/inventory" element={<div>Mi stock page</div>} />
          <Route path="/closings" element={<div>Cierres page</div>} />
          <Route path="/closings/monthly" element={<div>Cierres mensuales page</div>} />
          <Route path="/commissions" element={<div>Configuracion page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
