import { MemoryRouter, Route, Routes } from "react-router-dom";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AppShell } from "@/shared/components/layout/AppShell";

const sessionMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => sessionMock(),
}));

beforeEach(() => {
  sessionMock.mockReset();
});

afterEach(() => {
  cleanup();
});

describe("AppShell", () => {
  it("keeps Administrador General navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Admin", marketIds: [], email: "admin@example.com" },
      isLoading: false,
      roles: ["ADMIN_SYSTEM"],
      primaryRole: "ADMIN_SYSTEM",
      visibleRoleLabel: "Administrador General",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Tiendas")).toBeInTheDocument();
    expect(screen.getByText("Reportes")).toBeInTheDocument();
    expect(screen.getByText("Acceso global")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Tiendas" }));

    expect(screen.getByText("Tiendas")).toBeInTheDocument();
    expect(screen.getByText("Colaboradores")).toBeInTheDocument();
    expect(screen.getByText("Configuracion")).toBeInTheDocument();
    expect(screen.queryByText("Ventas")).not.toBeInTheDocument();
  });

  it("keeps Administrador de Tienda navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Admin Tienda", marketIds: [2], email: "admin.tienda@example.com", activeMarketName: "Sakura Store" },
      isLoading: false,
      roles: ["ADMIN_MARKET"],
      primaryRole: "ADMIN_MARKET",
      visibleRoleLabel: "Administrador de Tienda",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Stock")).toBeInTheDocument();
    expect(screen.getByText("Nueva venta")).toBeInTheDocument();
    expect(screen.getByText("Sakura Store")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Stock" }));

    expect(screen.getByText("Colaboradores")).toBeInTheDocument();
    expect(screen.getByText("Ventas")).toBeInTheDocument();
    expect(screen.getByText("Configuracion")).toBeInTheDocument();
    expect(screen.queryByText("Tiendas")).not.toBeInTheDocument();
  });

  it("keeps collaborator navigation visible across route changes", async () => {
    sessionMock.mockReturnValue({
      user: { fullName: "Colaborador", marketIds: [3], email: "colab@example.com" },
      isLoading: false,
      roles: ["STORE_USER"],
      primaryRole: "STORE_USER",
      visibleRoleLabel: "Colaborador",
      logoutUrl: "/logout",
    });

    const user = userEvent.setup();
    renderShell("/dashboard");

    expect(screen.getByText("Mis ventas")).toBeInTheDocument();
    expect(screen.getByText("Mi stock")).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Mi stock" }));

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Mis ventas")).toBeInTheDocument();
    expect(screen.getByText("Mi stock")).toBeInTheDocument();
    expect(screen.queryByText("Nueva venta")).not.toBeInTheDocument();
    expect(screen.queryByText("Tiendas")).not.toBeInTheDocument();
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
    expect(screen.queryByText("Tiendas")).not.toBeInTheDocument();
    expect(screen.queryByText("Ventas")).not.toBeInTheDocument();
  });
});

function renderShell(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<div>Dashboard page</div>} />
          <Route path="/tiendas" element={<div>Tiendas page</div>} />
          <Route path="/colaboradores" element={<div>Colaboradores page</div>} />
          <Route path="/sales" element={<div>Ventas page</div>} />
          <Route path="/sales/today" element={<div>Mis ventas page</div>} />
          <Route path="/products" element={<div>Stock page</div>} />
          <Route path="/inventory" element={<div>Mi stock page</div>} />
          <Route path="/closings" element={<div>Cierres page</div>} />
          <Route path="/commissions" element={<div>Configuracion page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
