import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DailyClosingPage } from "@/features/closings/pages/DailyClosingPage";
import { ApiError } from "@/shared/lib/api/client";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    roles: ["ADMIN_SYSTEM"],
  }),
}));

vi.mock("@/features/markets/api/marketApi", () => ({
  listMarkets: vi.fn(),
}));

vi.mock("@/features/closings/api/closingApi", () => ({
  getDailyClosing: vi.fn(),
  closeDaily: vi.fn(),
}));

import { closeDaily, getDailyClosing } from "@/features/closings/api/closingApi";
import { listMarkets } from "@/features/markets/api/marketApi";

describe("DailyClosingPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows an empty state when the closing query returns 404", async () => {
    vi.mocked(listMarkets).mockResolvedValue([
      {
        id: 1,
        name: "Mercado Creativo",
        email: "admin@mercado.cl",
        phone: null,
        contactName: null,
        description: null,
        city: "Santiago",
        currency: "CLP",
        ufEnabled: true,
        active: true,
        createdAt: "2026-03-15T00:00:00Z",
        updatedAt: "2026-03-15T00:00:00Z",
      },
    ]);
    vi.mocked(getDailyClosing).mockRejectedValue(new ApiError("Not found", 404));
    vi.mocked(closeDaily).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingDate: "2026-03-15",
      saleCount: 0,
      totalSalesAmount: 0,
      totalCommissionAmount: 0,
      totalNetAmount: 0,
      closedAt: "2026-03-15T00:00:00Z",
      closedBy: "system",
      stores: [],
    });

    const user = userEvent.setup();
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <DailyClosingPage />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Tienda"), "1");

    await waitFor(() => {
      expect(screen.getByText("No existe cierre para esa fecha")).toBeInTheDocument();
    });
  });

  it("disables closing actions while the daily closing is being generated", async () => {
    vi.mocked(listMarkets).mockResolvedValue([
      {
        id: 1,
        name: "Mercado Creativo",
        email: "admin@mercado.cl",
        phone: null,
        contactName: null,
        description: null,
        city: "Santiago",
        currency: "CLP",
        ufEnabled: true,
        active: true,
        createdAt: "2026-03-15T00:00:00Z",
        updatedAt: "2026-03-15T00:00:00Z",
      },
    ]);
    vi.mocked(getDailyClosing).mockRejectedValue(new ApiError("Not found", 404));

    let resolveClose: ((value: any) => void) | undefined;
    vi.mocked(closeDaily).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveClose = resolve;
        }),
    );

    const user = userEvent.setup();
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <DailyClosingPage />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Tienda"), "1");

    await waitFor(() => {
      expect(screen.getByText("No existe cierre para esa fecha")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Generar cierre" }));

    expect(screen.getByRole("button", { name: "Cerrando..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Consultar cierre" })).toBeDisabled();

    if (resolveClose) {
      resolveClose({
        marketId: 1,
        marketName: "Mercado Creativo",
        closingDate: "2026-03-15",
        saleCount: 0,
        totalSalesAmount: 0,
        totalCommissionAmount: 0,
        totalNetAmount: 0,
        closedAt: "2026-03-15T00:00:00Z",
        closedBy: "system",
        stores: [],
      });
    }

    await waitFor(() => {
      expect(screen.getByText("Cierre diario generado correctamente.")).toBeInTheDocument();
    });
  });
});
