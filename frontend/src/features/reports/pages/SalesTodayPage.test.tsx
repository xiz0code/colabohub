import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SalesTodayPage } from "@/features/reports/pages/SalesTodayPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    primaryRole: "STORE_USER",
  }),
}));

vi.mock("@/features/reports/api/reportApi", () => ({
  getSalesTodayDetails: vi.fn(),
}));

import { getSalesTodayDetails } from "@/features/reports/api/reportApi";

describe("SalesTodayPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders collaborator empty state for Mis ventas", async () => {
    vi.mocked(getSalesTodayDetails).mockResolvedValue({
      businessDate: "2026-03-15",
      totalSales: 0,
      totalAmount: 0,
      totalCommission: 0,
      totalNet: 0,
      salesCount: 0,
      stores: [],
      sales: [],
    });

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <SalesTodayPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Mis ventas")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("No tienes ventas visibles hoy")).toBeInTheDocument();
    });
    expect(screen.getByText("No hay ventas visibles para mostrar")).toBeInTheDocument();
  });
});
