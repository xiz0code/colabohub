import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { UsersPage } from "@/features/users/pages/UsersPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    primaryRole: "ADMIN_MARKET",
  }),
}));

vi.mock("@/features/markets/api/marketApi", () => ({
  listMarkets: vi.fn(),
}));

vi.mock("@/features/users/api/userApi", () => ({
  createUser: vi.fn(),
  listUsers: vi.fn(),
  updateUser: vi.fn(),
  updateUserStatus: vi.fn(),
}));

import { listMarkets } from "@/features/markets/api/marketApi";
import { createUser, listUsers } from "@/features/users/api/userApi";

describe("UsersPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listMarkets).mockResolvedValue([
      {
        id: 7,
        name: "Sakura Store",
        email: "sakura@colabohub.cl",
        phone: null,
        contactName: null,
        description: null,
        city: "Santiago",
        currency: "CLP",
        ufEnabled: true,
        active: true,
        createdAt: "2026-03-16T10:00:00Z",
        updatedAt: "2026-03-16T10:00:00Z",
      },
    ]);
    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 1,
        email: "camila@colabohub.cl",
        fullName: "Camila Soto",
        phone: "+56999999999",
        contactName: "Camila",
        description: "Stand de stickers",
        monthlyRent: 120000,
        startDate: "2026-03-01",
        standNumber: "Stand 12",
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [7],
        storeIds: [],
        active: true,
        createdAt: "2026-03-16T12:00:00Z",
        updatedAt: "2026-03-16T12:00:00Z",
      },
    ]);
  });

  it("muestra fecha de creacion y no pide seleccionar espacio a admin de espacio para tiendas", async () => {
    const user = userEvent.setup();
    renderPage("stores");

    expect(await screen.findByText("Fecha creacion")).toBeInTheDocument();
    expect(await screen.findByText("Camila Soto")).toBeInTheDocument();
    expect(screen.getByText(/2026/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Nueva Tienda" }));

    await waitFor(() => {
      expect(screen.getByText("Esta Tienda se asignara automaticamente al Espacio activo que administras.")).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Espacio")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Esta Tienda emite factura")).not.toBeChecked();
  });

  it("permite crear vendedor sin selector de espacio para admin de espacio", async () => {
    vi.mocked(createUser).mockResolvedValue({
      id: 2,
      email: "seller@colabohub.cl",
      fullName: "Vendedor Demo",
      phone: null,
      contactName: null,
      description: null,
      monthlyRent: null,
      startDate: null,
      standNumber: null,
      factura: false,
      roles: ["SELLER"],
      marketIds: [7],
      storeIds: [],
      active: true,
      createdAt: "2026-03-16T12:00:00Z",
      updatedAt: "2026-03-16T12:00:00Z",
    });

    const user = userEvent.setup();
    renderPage("sellers");

    await user.click(screen.getByRole("button", { name: "Nuevo vendedor" }));

    expect(screen.getByText("Este vendedor se asignara automaticamente al Espacio activo que administras.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Espacio")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Nombre del vendedor"), "Vendedor Demo");
    await user.type(screen.getByLabelText("Correo de login"), "seller@colabohub.cl");
    await user.type(screen.getByLabelText("RUT"), "11.111.111-1");
    await user.click(screen.getByRole("button", { name: "Crear vendedor" }));

    expect(createUser).toHaveBeenCalled();
    expect(vi.mocked(createUser).mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({ role: "SELLER", contactName: "11.111.111-1" }),
    );
  });
});

function renderPage(mode: "stores" | "sellers" = "stores") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <UsersPage mode={mode} />
    </QueryClientProvider>,
  );
}
