import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DashboardPage } from "@/features/dashboard/pages/DashboardPage";

const sessionMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => sessionMock(),
}));

vi.mock("@/features/reports/api/reportApi", () => ({
  getDashboardSummary: vi.fn(),
  getSalesTodayDetails: vi.fn(),
}));

import { getDashboardSummary, getSalesTodayDetails } from "@/features/reports/api/reportApi";

describe("DashboardPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    sessionMock.mockReturnValue({
      primaryRole: "STORE_USER",
      user: { id: 13, fullName: "PKM Store" },
    });
    vi.mocked(getDashboardSummary).mockResolvedValue({
      businessDate: "2026-06-23",
      salesCount: 3,
      totalAmount: 28500,
      totalCommission: 2850,
      totalNet: 25650,
      activeProducts: 120,
      lowStockProducts: 16,
      pendingPickups: 4,
      previousDaySalesCount: 2,
      previousDayAmount: 18000,
      salesChangePercentage: 58.33,
      trend: [
        { date: "2026-06-17", salesCount: 1, totalAmount: 9000, totalNet: 8100 },
        { date: "2026-06-18", salesCount: 2, totalAmount: 15000, totalNet: 13500 },
        { date: "2026-06-19", salesCount: 0, totalAmount: 0, totalNet: 0 },
        { date: "2026-06-20", salesCount: 4, totalAmount: 32000, totalNet: 28800 },
        { date: "2026-06-21", salesCount: 3, totalAmount: 24000, totalNet: 21600 },
        { date: "2026-06-22", salesCount: 2, totalAmount: 18000, totalNet: 16200 },
        { date: "2026-06-23", salesCount: 3, totalAmount: 28500, totalNet: 25650 },
      ],
      paymentMethods: [
        { paymentMethod: "DEBITO", salesCount: 2, totalAmount: 20000 },
        { paymentMethod: "CASH", salesCount: 1, totalAmount: 8500 },
      ],
      stores: [],
    });
    vi.mocked(getSalesTodayDetails).mockResolvedValue({
      businessDate: "2026-06-23",
      totalSales: 28500,
      totalAmount: 28500,
      totalCommission: 2850,
      totalNet: 25650,
      salesCount: 3,
      stores: [],
      sales: [
        {
          saleId: 10,
          saleNumber: "V-0010",
          confirmedAt: "2026-06-23T12:00:00Z",
          totalAmount: 8500,
          totalCommissionAmount: 850,
          totalNetAmount: 7650,
          stores: [],
          items: [
            {
              itemId: 100,
              productName: "Llavero BTS",
              collaboratorName: "PKM Store",
              storeName: "Stock principal",
              quantity: 1,
              subtotalAmount: 8500,
              totalCommissionAmount: 850,
              netAmount: 7650,
            },
          ],
        },
        {
          saleId: 11,
          saleNumber: "V-0011",
          confirmedAt: "2026-06-23T16:30:00Z",
          totalAmount: 20000,
          totalCommissionAmount: 2000,
          totalNetAmount: 18000,
          stores: [],
          items: [
            {
              itemId: 101,
              productName: "Set Photocards",
              collaboratorName: "PKM Store",
              storeName: "Stock principal",
              quantity: 2,
              subtotalAmount: 20000,
              totalCommissionAmount: 2000,
              netAmount: 18000,
            },
          ],
        },
      ],
    });
  });

  it("shows comparisons, payment methods and actionable alerts for a store", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <DashboardPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Pulso comercial de hoy")).toBeInTheDocument();
    expect(screen.getByText("+58,3% respecto de ayer")).toBeInTheDocument();
    expect(screen.getByText("Medios de pago")).toBeInTheDocument();
    expect(screen.getByText("Débito")).toBeInTheDocument();
    expect(screen.getByText("Stock crítico")).toBeInTheDocument();
    expect(screen.getByText("Retiros pendientes")).toBeInTheDocument();
    expect(screen.getByText("Últimas ventas confirmadas de hoy")).toBeInTheDocument();
    const recentSales = screen.getAllByText(/V-001[01]/);
    expect(recentSales[0]).toHaveTextContent("V-0011");
    expect(screen.getByText("2 x Set Photocards")).toBeInTheDocument();
  });
});
