import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SalesPage } from "@/features/sales/pages/SalesPage";

const openPosMock = vi.fn();
const openPosWithSaleMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    user: {
      id: 1,
      email: "admin.tienda@colabohub.cl",
      fullName: "Admin Tienda",
      active: true,
      roles: ["ADMIN_MARKET"],
      activeMarketId: 1,
      activeMarketName: "Sakura Store",
      marketIds: [1],
      storeIds: [],
    },
    primaryRole: "ADMIN_MARKET",
  }),
}));

vi.mock("@/features/sales/components/PosLauncherProvider", () => ({
  usePosLauncher: () => ({
    openPos: openPosMock,
    openPosWithSale: openPosWithSaleMock,
    canOperatePos: true,
    isOpening: false,
  }),
}));

vi.mock("@/features/markets/api/marketApi", () => ({
  listMarkets: vi.fn(),
}));

vi.mock("@/features/sales/api/posApi", () => ({
  addPosSaleItem: vi.fn(),
  cancelPosSale: vi.fn(),
  editPosSale: vi.fn(),
  confirmPosSale: vi.fn(),
  createPosSale: vi.fn(),
  getPosSale: vi.fn(),
  listPosSales: vi.fn(),
  recalculatePosSale: vi.fn(),
  removePosSaleItem: vi.fn(),
  scanPosProduct: vi.fn(),
  searchPosProducts: vi.fn(),
  updatePosPaymentMethod: vi.fn(),
  updatePosSaleItem: vi.fn(),
}));

import {
  cancelPosSale,
  editPosSale,
  confirmPosSale,
  createPosSale,
  getPosSale,
  listPosSales,
  searchPosProducts,
  updatePosPaymentMethod,
} from "@/features/sales/api/posApi";
import { listMarkets } from "@/features/markets/api/marketApi";

function buildSale(status: "OPEN" | "CONFIRMED" | "CANCELLED" = "OPEN") {
  return {
    id: 10,
    saleNumber: "S-2026-00000010",
    marketId: 1,
    status,
    paymentMethod: "DEBITO" as const,
    netAmount: 24285,
    ivaAmount: 4615,
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
    cancelledAt: status === "CANCELLED" ? "2026-03-15T12:20:00Z" : null,
    cancelledBy: status === "CANCELLED" ? "admin.tienda@colabohub.cl" : null,
    cancellationReason: status === "CANCELLED" ? "Cliente solicito anulacion" : null,
    items: [
      {
        id: 99,
        productId: 1000,
        manualEntry: false,
        manualReference: null,
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
        promotionApplied: false,
        ufValue: 39000,
        commissionUfValue: 0.00169,
        commissionPercentageValue: 0.0079,
        totalCollaboratorAmount: 28550,
        totalClientAmount: 28900,
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

function buildSales() {
  return {
    content: [
      {
        id: 10,
        saleNumber: "S-2026-00000010",
        type: "POS",
        dateTime: "2026-03-15T12:10:00Z",
        status: "CONFIRMED" as const,
        subtotalAmount: 28900,
        netAmount: 24285,
        ivaAmount: 4615,
        totalAmount: 28900,
        paymentMethod: "DEBITO" as const,
        marketId: 1,
        sellerName: "Admin Tienda",
      },
    ],
    page: 0,
    size: 25,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
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
    openPosMock.mockReset();
    openPosWithSaleMock.mockReset();
    vi.mocked(listPosSales).mockResolvedValue(buildSales());
    vi.mocked(listMarkets).mockResolvedValue([
      {
        id: 1,
        name: "Sakura Store",
        email: "sakura@example.com",
        phone: null,
        contactName: null,
        description: null,
        city: "Santiago",
        currency: "CLP",
        ufEnabled: true,
        active: true,
        createdAt: "2026-03-15T12:00:00Z",
        updatedAt: "2026-03-15T12:00:00Z",
      },
    ]);
    vi.mocked(getPosSale).mockResolvedValue(buildSale("CONFIRMED"));
    vi.mocked(editPosSale).mockResolvedValue(buildSale("OPEN"));
    vi.mocked(searchPosProducts).mockResolvedValue([]);
  });

  it("usa el launcher global al pedir una nueva venta", async () => {
    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    expect(openPosMock).toHaveBeenCalledTimes(1);
  });

  it("muestra el detalle de venta con tienda y datos de comision", async () => {
    renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Ver detalle" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Ver detalle" }));

    await waitFor(() => {
      expect(screen.getByText("Detalle S-2026-00000010")).toBeInTheDocument();
    });

    expect(screen.getByText("Camila")).toBeInTheDocument();
    expect(screen.getByText("Comision fija")).toBeInTheDocument();
  });

  it("solicita motivo obligatorio al anular una venta", async () => {
    vi.mocked(cancelPosSale).mockResolvedValue(buildSale("CANCELLED"));

    renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Anular venta" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Anular venta" }));

    await waitFor(() => {
      expect(screen.getByText("Motivo de anulacion")).toBeInTheDocument();
    });

    const confirmButton = screen.getByRole("button", { name: "Confirmar anulacion" });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByRole("textbox"), "Cliente solicito anulacion");
    expect(confirmButton).not.toBeDisabled();

    await user.click(confirmButton);

    await waitFor(() => {
      expect(cancelPosSale).toHaveBeenCalledWith(10, "Cliente solicito anulacion");
    });
  });

  it("abre una venta confirmada para editarla en el POS", async () => {
    renderPage();

    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Editar" }));

    await waitFor(() => {
      expect(editPosSale).toHaveBeenCalledWith(10);
      expect(openPosWithSaleMock).toHaveBeenCalled();
    });
  });

});
