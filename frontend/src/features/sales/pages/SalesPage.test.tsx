import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SalesPage } from "@/features/sales/pages/SalesPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    user: {
      id: 1,
      email: "admin.tienda@colaborapp.cl",
      fullName: "Admin Tienda",
      roles: ["ADMIN_MARKET"],
      marketIds: [1],
      storeIds: [],
    },
    primaryRole: "ADMIN_MARKET",
  }),
}));

vi.mock("@/features/reports/api/reportApi", () => ({
  getSalesTodayDetails: vi.fn(),
}));

vi.mock("@/features/sales/api/posApi", () => ({
  addPosSaleItem: vi.fn(),
  cancelPosSale: vi.fn(),
  confirmPosSale: vi.fn(),
  createPosSale: vi.fn(),
  getOpenPosSale: vi.fn(),
  recalculatePosSale: vi.fn(),
  removePosSaleItem: vi.fn(),
  scanPosProduct: vi.fn(),
  searchPosProducts: vi.fn(),
  updatePosPaymentMethod: vi.fn(),
  updatePosSaleItem: vi.fn(),
}));

import { getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { confirmPosSale, createPosSale, getOpenPosSale } from "@/features/sales/api/posApi";

function buildSale(status: "OPEN" | "CONFIRMED" | "CANCELLED" = "OPEN") {
  return {
    id: 10,
    saleNumber: "S-2026-00000010",
    status,
    paymentMethod: "DEBITO" as const,
    subtotalAmount: 28900,
    totalDiscountAmount: 0,
    totalAmount: 28900,
    totalCommissionAmount: 890,
    totalNetAmount: 28010,
    ufValue: 39000,
    commissionUfValue: 0.00169,
    commissionPercentageValue: 0.0079,
    openedAt: "2026-03-15T12:00:00Z",
    confirmedAt: status === "CONFIRMED" ? "2026-03-15T12:10:00Z" : null,
    items: [
      {
        id: 99,
        productId: 1000,
        storeId: 200,
        storeName: "Tienda Central",
        productName: "Sticker BTS",
        collaboratorName: "Camila",
        sku: "SKU-1",
        barcode: "BAR-1",
        quantity: 1,
        baseUnitPrice: 28900,
        lineBaseSubtotal: 28900,
        promotionDiscountAmount: 0,
        subtotal: 28900,
        pricingType: "NORMAL" as const,
        appliedPromotionId: null,
        appliedPromotionName: null,
        commission1Amount: 66,
        commission2Amount: 228,
        commissionIvaAmount: 56,
        totalCommissionAmount: 350,
        netAmount: 28550,
      },
    ],
    storeSummaries: [
      {
        storeId: 200,
        storeName: "Tienda Central",
        lineCount: 1,
        unitCount: 1,
        subtotalAmount: 28900,
        commission1Amount: 66,
        commission2Amount: 228,
        commissionIvaAmount: 56,
        totalCommissionAmount: 350,
        netAmount: 28550,
      },
    ],
  };
}

function buildSalesTodayDetails() {
  return {
    businessDate: "2026-03-15",
    totalSales: 1,
    totalAmount: 28900,
    totalCommission: 350,
    totalNet: 28550,
    salesCount: 1,
    stores: [],
    sales: [
      {
        saleId: 10,
        saleNumber: "S-2026-00000010",
        confirmedAt: "2026-03-15T12:10:00Z",
        totalAmount: 28900,
        totalCommissionAmount: 350,
        totalNetAmount: 28550,
        stores: [],
        items: [
          {
            itemId: 99,
            productName: "Sticker BTS",
            collaboratorName: "Camila",
            storeName: "Tienda Central",
            quantity: 1,
            subtotalAmount: 28900,
            totalCommissionAmount: 350,
            netAmount: 28550,
          },
        ],
      },
    ],
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <SalesPage />
    </QueryClientProvider>,
  );
}

describe("SalesPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getSalesTodayDetails).mockResolvedValue(buildSalesTodayDetails());
    vi.mocked(createPosSale).mockResolvedValue(buildSale("OPEN"));
  });

  it("does not render a Tienda selector and highlights total a cobrar", async () => {
    vi.mocked(getOpenPosSale).mockResolvedValue(buildSale("OPEN"));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/Total a cobrar/i)).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Tienda activa")).not.toBeInTheDocument();
    expect(screen.getAllByText("$28.900").length).toBeGreaterThan(0);
    expect(screen.getByText("Camila")).toBeInTheDocument();
  });

  it("communicates when an existing open sale is reused", async () => {
    vi.mocked(getOpenPosSale).mockResolvedValue(buildSale("OPEN"));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Ya existia una venta abierta para tu Tienda y se reutilizo.")).toBeInTheDocument();
    });
  });

  it("requires a confirmation modal before confirming", async () => {
    vi.mocked(getOpenPosSale).mockResolvedValue(buildSale("OPEN"));

    renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Confirmar venta" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Confirmar venta" }));

    expect(screen.getByText("Deseas confirmar esta venta?")).toBeInTheDocument();
    expect(vi.mocked(confirmPosSale)).not.toHaveBeenCalled();
  });

  it("confirms the sale, closes the modal and refreshes daily sales feedback", async () => {
    vi.mocked(getOpenPosSale).mockResolvedValue(buildSale("OPEN"));
    vi.mocked(confirmPosSale).mockResolvedValue(buildSale("CONFIRMED"));

    renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Confirmar venta" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Confirmar venta" }));
    await user.click(screen.getAllByRole("button", { name: "Confirmar venta" })[1]);

    await waitFor(() => {
      expect(screen.getByText("Venta registrada correctamente")).toBeInTheDocument();
    });

    expect(screen.queryByText("Deseas confirmar esta venta?")).not.toBeInTheDocument();
    expect(screen.getByText("Ventas registradas hoy")).toBeInTheDocument();
  });
});
