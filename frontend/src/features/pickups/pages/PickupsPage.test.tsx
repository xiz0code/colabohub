import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { PickupsPage } from "@/features/pickups/pages/PickupsPage";

const sessionMock = vi.fn();
const openPosWithSaleMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => sessionMock(),
}));

vi.mock("@/features/sales/components/PosLauncherProvider", () => ({
  usePosLauncher: () => ({
    openPosWithSale: openPosWithSaleMock,
  }),
}));

vi.mock("@/features/pickups/api/pickupsApi", () => ({
  listPickups: vi.fn(),
  createPickup: vi.fn(),
  checkoutPickup: vi.fn(),
  checkoutPickupByCode: vi.fn(),
  collectPickup: vi.fn(),
  cancelPickup: vi.fn(),
  downloadPickupLabel: vi.fn(),
}));

vi.mock("@/features/users/api/userApi", () => ({
  listUsers: vi.fn(),
}));

import { cancelPickup, checkoutPickup, collectPickup, createPickup, listPickups } from "@/features/pickups/api/pickupsApi";
import { listUsers } from "@/features/users/api/userApi";

describe("PickupsPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    sessionMock.mockReturnValue({
      primaryRole: "ADMIN_MARKET",
      user: {
        id: 50,
        fullName: "Leon",
        active: true,
        activeMarketId: 2,
        activeMarketName: "Tienda Leon",
        marketIds: [2],
        storeIds: [],
      },
    });

    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 7,
        email: "tienda.felipe@example.com",
        fullName: "Tienda Felipe",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [2],
        storeIds: [5],
        active: true,
        createdAt: "2026-03-22T12:00:00Z",
        updatedAt: "2026-03-22T12:00:00Z",
      },
      {
        id: 8,
        email: "tienda.igor@example.com",
        fullName: "Tienda Igor",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [2],
        storeIds: [9],
        active: true,
        createdAt: "2026-03-22T12:00:00Z",
        updatedAt: "2026-03-22T12:00:00Z",
      },
    ]);

    vi.mocked(listPickups).mockResolvedValue([
      {
        id: 100,
        marketId: 2,
        marketName: "Tienda Leon",
        storeId: 5,
        storeName: "Tienda Felipe",
        pickupBarcode: "RET-0000100",
        pickupNumber: "IG115",
        customerName: "Karina",
        description: "Polera personalizada negra",
        payable: true,
        amountDue: 12000,
        status: "PENDING",
        linkedSaleId: null,
        collectedAt: null,
        collectedBy: null,
        createdAt: "2026-03-22T12:00:00Z",
      },
      {
        id: 101,
        marketId: 2,
        marketName: "Tienda Leon",
        storeId: 9,
        storeName: "Tienda Igor",
        pickupBarcode: "RET-0000101",
        pickupNumber: "#1004",
        customerName: "Martin",
        description: "Pedido web ya pagado",
        payable: false,
        amountDue: null,
        status: "COLLECTED",
        linkedSaleId: 500,
        collectedAt: "2026-03-22T13:10:00Z",
        collectedBy: "seller@example.com",
        createdAt: "2026-03-22T10:00:00Z",
      },
    ]);

    vi.mocked(createPickup).mockResolvedValue({
      id: 102,
      marketId: 2,
      marketName: "Tienda Leon",
      storeId: 5,
      storeName: "Tienda Felipe",
      pickupBarcode: "RET-0000102",
      pickupNumber: "WEB-200",
      customerName: "Javiera",
      description: "Pedido Instagram",
      payable: true,
      amountDue: 15500,
      status: "PENDING",
      linkedSaleId: null,
      collectedAt: null,
      collectedBy: null,
      createdAt: "2026-03-22T14:00:00Z",
    });

    vi.mocked(checkoutPickup).mockResolvedValue({
      pickup: {
        id: 100,
        marketId: 2,
        marketName: "Tienda Leon",
        storeId: 5,
        storeName: "Tienda Felipe",
        pickupBarcode: "RET-0000100",
        pickupNumber: "IG115",
        customerName: "Karina",
        description: "Polera personalizada negra",
        payable: true,
        amountDue: 12000,
        status: "CHECKOUT_IN_PROGRESS",
        linkedSaleId: 70,
        collectedAt: null,
        collectedBy: null,
        createdAt: "2026-03-22T12:00:00Z",
      },
      sale: {
        id: 70,
        saleNumber: "S-2026-00000009",
        marketId: 2,
        status: "OPEN",
        paymentMethod: "CASH",
        netAmount: 10084,
        ivaAmount: 1916,
        subtotalAmount: 12000,
        totalDiscountAmount: 0,
        totalAmount: 12000,
        totalCommissionAmount: 0,
        totalNetAmount: 12000,
        ufValue: null,
        commissionUfValue: null,
        commissionPercentageValue: null,
        openedAt: "2026-03-22T12:00:00Z",
        confirmedAt: null,
        cancelledAt: null,
        cancelledBy: null,
        cancellationReason: null,
        items: [],
        storeSummaries: [],
      },
    });

    vi.mocked(collectPickup).mockResolvedValue({
      id: 103,
      marketId: 2,
      marketName: "Tienda Leon",
      storeId: 5,
      storeName: "Tienda Felipe",
      pickupBarcode: "RET-0000103",
      pickupNumber: "PAG-30",
      customerName: "Rocio",
      description: "Pedido ya pagado",
      payable: false,
      amountDue: null,
      status: "COLLECTED",
      linkedSaleId: null,
      collectedAt: "2026-03-22T14:00:00Z",
      collectedBy: "leon@example.com",
      createdAt: "2026-03-22T09:00:00Z",
    });

    vi.mocked(cancelPickup).mockResolvedValue({
      id: 104,
      marketId: 2,
      marketName: "Tienda Leon",
      storeId: 5,
      storeName: "Tienda Felipe",
      pickupBarcode: "RET-0000104",
      pickupNumber: "CAN-11",
      customerName: "Paula",
      description: "Retiro anulado",
      payable: true,
      amountDue: 9900,
      status: "CANCELLED",
      linkedSaleId: null,
      collectedAt: null,
      collectedBy: null,
      createdAt: "2026-03-22T09:00:00Z",
    });
  });

  it("shows operational metrics and lets admin create a pickup", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Monto por cobrar")).toBeInTheDocument();
    await screen.findByText("IG115");
    expect(screen.getByText("$12.000")).toBeInTheDocument();
    expect(screen.getByText("Retiro pagado vs retiro por pagar")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Nuevo retiro" }));

    await user.selectOptions(screen.getByLabelText("Tienda"), "5");
    await user.type(screen.getByLabelText("Numero de retiro"), "WEB-200");
    await user.type(screen.getByLabelText("Nombre de quien retira"), "Javiera");
    await user.type(screen.getByLabelText("Descripcion"), "Pedido Instagram");
    await user.click(screen.getByLabelText("Retiro por pagar"));
    await user.type(screen.getByLabelText("Monto por cobrar"), "15500");
    await user.click(screen.getByRole("button", { name: "Crear retiro" }));

    await waitFor(() => {
      expect(createPickup).toHaveBeenCalledWith({
        storeId: 5,
        pickupNumber: "WEB-200",
        customerName: "Javiera",
        description: "Pedido Instagram",
        payable: true,
        amountDue: 15500,
      });
    });
  });

  it("loads a payable pickup into the POS", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText("IG115");
    await user.click(screen.getByRole("button", { name: "Cobrar retiro" }));

    await waitFor(() => {
      expect(checkoutPickup).toHaveBeenCalledWith(100);
      expect(openPosWithSaleMock).toHaveBeenCalledWith(expect.objectContaining({ id: 70 }));
    });
  });

  it("lets a store user mark paid pickups as collected", async () => {
    sessionMock.mockReturnValue({
      primaryRole: "STORE_USER",
      user: {
        id: 7,
        fullName: "Tienda Felipe",
        active: true,
        activeMarketId: 2,
        activeMarketName: "Tienda Leon",
        marketIds: [2],
        storeIds: [5],
      },
    });

    vi.mocked(listPickups).mockResolvedValue([
      {
        id: 103,
        marketId: 2,
        marketName: "Tienda Leon",
        storeId: 5,
        storeName: "Tienda Felipe",
        pickupBarcode: "RET-0000103",
        pickupNumber: "PAG-30",
        customerName: "Rocio",
        description: "Pedido ya pagado",
        payable: false,
        amountDue: null,
        status: "PENDING",
        linkedSaleId: null,
        collectedAt: null,
        collectedBy: null,
        createdAt: "2026-03-22T09:00:00Z",
      },
    ]);

    const user = userEvent.setup();
    renderPage();

    await screen.findByText("PAG-30");
    expect(screen.queryByRole("option", { name: "Todas las Tiendas" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Marcar retirado" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Anular retiro" })).toBeDisabled();
  });

  it("hides the store selector for store users and assumes their store on create", async () => {
    sessionMock.mockReturnValue({
      primaryRole: "STORE_USER",
      user: {
        id: 7,
        fullName: "Tienda Felipe",
        active: true,
        activeMarketId: 2,
        activeMarketName: "Tienda Leon",
        marketIds: [2],
        storeIds: [5],
      },
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText("IG115");
    await user.click(screen.getByRole("button", { name: "Nuevo retiro" }));

    expect(screen.getAllByText("Tienda Felipe").length).toBeGreaterThan(0);
    expect(screen.queryByRole("option", { name: "Selecciona una Tienda" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Numero de retiro"), "WEB-201");
    await user.type(screen.getByLabelText("Nombre de quien retira"), "Claudia");
    await user.type(screen.getByLabelText("Descripcion"), "Pedido web con retiro en mostrador");
    await user.click(screen.getByRole("button", { name: "Crear retiro" }));

    await waitFor(() => {
      expect(createPickup).toHaveBeenCalledWith({
        pickupNumber: "WEB-201",
        customerName: "Claudia",
        description: "Pedido web con retiro en mostrador",
        payable: false,
      });
    });
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
      <PickupsPage />
    </QueryClientProvider>,
  );
}
