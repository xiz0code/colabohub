import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

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
import { listUsers } from "@/features/users/api/userApi";

describe("UsersPage", () => {
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
        roles: ["STORE_USER"],
        marketIds: [7],
        storeIds: [],
        active: true,
        createdAt: "2026-03-16T12:00:00Z",
        updatedAt: "2026-03-16T12:00:00Z",
      },
    ]);
  });

  it("muestra fecha de creacion y no pide seleccionar espacio a admin de espacio", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Fecha creacion")).toBeInTheDocument();
    expect(await screen.findByText("Camila Soto")).toBeInTheDocument();
    expect(screen.getByText(/2026/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Nueva Tienda" }));

    await waitFor(() => {
      expect(screen.getByText("Esta Tienda se asignara automaticamente al Espacio activo que administras.")).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Espacio")).not.toBeInTheDocument();
  });
});

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <UsersPage />
    </QueryClientProvider>,
  );
}
