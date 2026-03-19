import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SalesPage } from "@/features/sales/pages/SalesPage";

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

vi.mock("@/features/sales/api/posApi", () => ({
  addPosSaleItem: vi.fn(),
  cancelPosSale: vi.fn(),
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
  confirmPosSale,
  createPosSale,
  getPosSale,
  listPosSales,
  searchPosProducts,
  updatePosPaymentMethod,
} from "@/features/sales/api/posApi";

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
  return [
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
  ];
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
    vi.mocked(listPosSales).mockResolvedValue(buildSales());
    vi.mocked(createPosSale).mockResolvedValue(buildSale("OPEN"));
    vi.mocked(getPosSale).mockResolvedValue(buildSale("CONFIRMED"));
    vi.mocked(searchPosProducts).mockResolvedValue([]);
  });

  it("abre una nueva venta sin selector de Espacio y muestra el total a cobrar", async () => {
    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    await waitFor(() => {
      expect(screen.getByText(/TOTAL A COBRAR/i)).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Espacio activo")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Ventas de Sakura Store/i })).toBeInTheDocument();
    expect(screen.getAllByText("$28.900").length).toBeGreaterThan(0);
  });

  it("requiere modal de confirmacion antes de confirmar la venta", async () => {
    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Confirmar venta" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Confirmar venta" }));

    expect(screen.getByText("Deseas confirmar esta venta?")).toBeInTheDocument();
    expect(vi.mocked(confirmPosSale)).not.toHaveBeenCalled();
  });

  it("confirma la venta y refresca el listado principal", async () => {
    vi.mocked(confirmPosSale).mockResolvedValue(buildSale("CONFIRMED"));

    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Confirmar venta" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Confirmar venta" }));
    await user.click(screen.getAllByRole("button", { name: "Confirmar venta" })[1]);

    await waitFor(() => {
      expect(screen.getByText("Venta registrada correctamente")).toBeInTheDocument();
    });

    expect(listPosSales).toHaveBeenCalled();
    expect(screen.getByText("Ventas de Sakura Store")).toBeInTheDocument();
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

  it("intercepta el cierre del POS cuando ya hay productos y permite seguir vendiendo", async () => {
    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    await waitFor(() => {
      expect(screen.getByText(/TOTAL A COBRAR/i)).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Cerrar" }));

    await waitFor(() => {
      expect(screen.getByText("Cancelar venta en curso")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Seguir vendiendo" }));

    expect(screen.queryByText("Cancelar venta en curso")).not.toBeInTheDocument();
    expect(cancelPosSale).not.toHaveBeenCalled();
    expect(screen.getByText(/TOTAL A COBRAR/i)).toBeInTheDocument();
  });

  it("calcula vuelto cuando la venta se cobra en efectivo", async () => {
    vi.mocked(updatePosPaymentMethod).mockResolvedValue({
      ...buildSale("OPEN"),
      paymentMethod: "CASH",
    });

    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    await waitFor(() => {
      expect(screen.getByRole("combobox")).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByRole("combobox"), "CASH");

    await waitFor(() => {
      expect(screen.getByText("Monto recibido")).toBeInTheDocument();
    });

    await user.type(screen.getByPlaceholderText("Ingresa el efectivo recibido"), "30000");

    expect(screen.getByText("$1.100")).toBeInTheDocument();
  });

  it("muestra el nombre de la tienda duena del producto en los resultados de busqueda del POS", async () => {
    vi.mocked(searchPosProducts).mockResolvedValue([
      {
        id: 201,
        storeId: 200,
        storeName: "Stock principal",
        collaboratorName: "Camila",
        name: "Photocard",
        sku: "PHOTOCARDS",
        barcode: "BAR-201",
        stock: 9,
        salePrice: 2000,
      },
      {
        id: 202,
        storeId: 200,
        storeName: "Stock principal",
        collaboratorName: "Karina",
        name: "Photocard K-pop",
        sku: "PHOTOCARDS-KPOP",
        barcode: "BAR-202",
        stock: 498,
        salePrice: 1000,
      },
    ]);

    renderPage();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Nueva venta" }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText("Busca por nombre, SKU o codigo de barras")).toBeInTheDocument();
    });

    await user.type(screen.getByPlaceholderText("Busca por nombre, SKU o codigo de barras"), "Photo");

    await waitFor(() => {
      expect(screen.getByText("Tienda: Camila")).toBeInTheDocument();
    });

    expect(screen.getByText("Tienda: Karina")).toBeInTheDocument();
    expect(screen.queryByText("Tienda: Stock principal")).not.toBeInTheDocument();
  });
});
