import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SalesTodayPage } from "@/features/reports/pages/SalesTodayPage";

const { mockUseSession } = vi.hoisted(() => ({
  mockUseSession: vi.fn(),
}));

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: mockUseSession,
}));

vi.mock("@/features/reports/api/reportApi", () => ({
  getSalesTodayDetails: vi.fn(),
  getCollaboratorSalesReport: vi.fn(),
  recalculateSalesCommissions: vi.fn(),
}));

vi.mock("@/features/users/api/userApi", () => ({
  listUsers: vi.fn(),
}));

vi.mock("@/shared/lib/files/downloadCsv", () => ({
  downloadCsv: vi.fn(),
}));

import { getCollaboratorSalesReport, getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { listUsers } from "@/features/users/api/userApi";
import { downloadCsv } from "@/shared/lib/files/downloadCsv";

describe("SalesTodayPage", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSession.mockReturnValue({
      primaryRole: "STORE_USER",
      user: {
        id: 7,
        fullName: "PKMSTORE",
      },
    });
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
    vi.mocked(listUsers).mockResolvedValue([]);
    vi.mocked(getCollaboratorSalesReport).mockResolvedValue({
      collaboratorUserId: 7,
      collaboratorName: "PKMSTORE",
      dateFrom: "2026-03-01",
      dateTo: "2026-03-15",
      totalAmount: 0,
      totalCommissionAmount: 0,
      totalNetAmount: 0,
      totalIvaAmount: 0,
      entries: [],
    });

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <SalesTodayPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Mis ventas")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("Detalle mensual de ventas")).toBeInTheDocument();
    });
    const dailySection = screen.getByTestId("daily-sales-section");
    const monthlySection = screen.getByTestId("monthly-sales-section");
    expect(dailySection).toHaveClass("order-1");
    expect(monthlySection).toHaveClass("order-2");
    expect(screen.getByText("Tu jornada de hoy")).toBeInTheDocument();
    expect(screen.getByText("Todavia no tienes ventas en este periodo")).toBeInTheDocument();
    expect(screen.getByText("Sin productos vendidos hoy")).toBeInTheDocument();
    expect(screen.getByText("Ultimas ventas confirmadas")).toBeInTheDocument();
  });

  it("renders the tienda report for admin users", async () => {
    mockUseSession.mockReturnValue({
      primaryRole: "ADMIN_SYSTEM",
      user: {
        id: 1,
        fullName: "Admin",
      },
    });
    vi.mocked(getSalesTodayDetails).mockResolvedValue({
      businessDate: "2026-03-15",
      totalSales: 1,
      totalAmount: 12500,
      totalCommission: 600,
      totalNet: 11900,
      salesCount: 1,
      stores: [],
      sales: [],
    });
    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 8,
        email: "camila@example.com",
        fullName: "Camila",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [1],
        storeIds: [1],
        active: true,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
    ]);
    vi.mocked(getCollaboratorSalesReport).mockResolvedValue({
      collaboratorUserId: 8,
      collaboratorName: "Camila",
      dateFrom: "2026-03-01",
      dateTo: "2026-03-15",
      totalAmount: 8900,
      totalCommissionAmount: 700,
      totalNetAmount: 8200,
      totalIvaAmount: 1300,
      entries: [
        {
          saleId: 10,
          saleNumber: "V-0010",
          confirmedAt: "2026-03-15T12:00:00Z",
          paymentMethod: "DEBIT",
          productName: "Sticker BTS",
          promotionLabel: "Sin promocion",
          quantity: 2,
          unitPrice: 2000,
          ufValue: 39000,
          fixedCommissionAmount: 33,
          variableCommissionAmount: 32,
          commissionIvaAmount: 12,
          totalAmount: 4000,
          commissionAmount: 320,
          netAmount: 3680,
        },
      ],
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
      <MemoryRouter initialEntries={["/reports/collaborators"]}>
        <QueryClientProvider client={queryClient}>
          <SalesTodayPage defaultTab="collaborators" />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Reporte por tienda")).toBeInTheDocument();
    const collaboratorSelect = await screen.findByLabelText("Tienda");
    await user.selectOptions(collaboratorSelect, "8");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Camila" })).toBeInTheDocument();
    });
    expect(screen.getAllByText("Sticker BTS").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Total a recibir").length).toBeGreaterThan(0);
    expect(screen.getByText("Comisión fija")).toBeInTheDocument();
  });

  it("hides inactive tiendas from the report selector", async () => {
    mockUseSession.mockReturnValue({
      primaryRole: "ADMIN_SYSTEM",
      user: {
        id: 1,
        fullName: "Admin",
      },
    });
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
    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 8,
        email: "camila@example.com",
        fullName: "Camila",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [1],
        storeIds: [1],
        active: true,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
      {
        id: 9,
        email: "inactiva@example.com",
        fullName: "Tienda Inactiva",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [1],
        storeIds: [2],
        active: false,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
    ]);

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });

    render(
      <MemoryRouter initialEntries={["/reports/collaborators"]}>
        <QueryClientProvider client={queryClient}>
          <SalesTodayPage defaultTab="collaborators" />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    const collaboratorSelect = await screen.findByLabelText("Tienda");
    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Camila" })).toBeInTheDocument();
    });
    expect(collaboratorSelect).not.toHaveTextContent("Tienda Inactiva");
  });

  it("downloads the collaborator report as csv", async () => {
    mockUseSession.mockReturnValue({
      primaryRole: "ADMIN_SYSTEM",
      user: {
        id: 1,
        fullName: "Admin",
      },
    });
    vi.mocked(getSalesTodayDetails).mockResolvedValue({
      businessDate: "2026-03-15",
      totalSales: 1,
      totalAmount: 12500,
      totalCommission: 600,
      totalNet: 11900,
      salesCount: 1,
      stores: [],
      sales: [],
    });
    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 8,
        email: "camila@example.com",
        fullName: "Camila",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        factura: false,
        roles: ["STORE_USER"],
        marketIds: [1],
        storeIds: [1],
        active: true,
        createdAt: "2026-03-01T00:00:00Z",
        updatedAt: "2026-03-01T00:00:00Z",
      },
    ]);
    vi.mocked(getCollaboratorSalesReport).mockResolvedValue({
      collaboratorUserId: 8,
      collaboratorName: "Camila",
      dateFrom: "2026-03-01",
      dateTo: "2026-03-15",
      totalAmount: 8900,
      totalCommissionAmount: 700,
      totalNetAmount: 8200,
      totalIvaAmount: 1300,
      entries: [
        {
          saleId: 10,
          saleNumber: "V-0010",
          confirmedAt: "2026-03-15T12:00:00Z",
          paymentMethod: "DEBIT",
          productName: "Sticker BTS",
          promotionLabel: "Sin promocion",
          quantity: 2,
          unitPrice: 2000,
          ufValue: 39000,
          fixedCommissionAmount: 33,
          variableCommissionAmount: 32,
          commissionIvaAmount: 12,
          totalAmount: 4000,
          commissionAmount: 320,
          netAmount: 3680,
        },
      ],
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
      <MemoryRouter initialEntries={["/reports/collaborators"]}>
        <QueryClientProvider client={queryClient}>
          <SalesTodayPage defaultTab="collaborators" />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await user.selectOptions(await screen.findByLabelText("Tienda"), "8");

    const downloadButtons = await screen.findAllByRole("button", { name: "Descargar CSV" });
    await user.click(downloadButtons[0]);

    expect(downloadCsv).toHaveBeenCalledWith(
      "ventas-tienda-camila-2026-03-01-a-2026-03-15.csv",
      expect.arrayContaining([
        expect.objectContaining({
          tienda: "Camila",
          producto: "Sticker BTS",
          cantidad: 2,
        }),
      ]),
    );
  });

  it("usa la fecha local para el rango inicial de Mis ventas", async () => {
    const RealDate = Date;
    class MockDate extends RealDate {
      constructor(...args: any[]) {
        if (args.length === 0) {
          super("2026-05-05T23:30:00-04:00");
          return;
        }
        super(...(args as [any]));
      }

      static now() {
        return new RealDate("2026-05-05T23:30:00-04:00").getTime();
      }
    }

    vi.stubGlobal("Date", MockDate as unknown as DateConstructor);

    vi.mocked(getSalesTodayDetails).mockResolvedValue({
      businessDate: "2026-05-05",
      totalSales: 0,
      totalAmount: 0,
      totalCommission: 0,
      totalNet: 0,
      salesCount: 0,
      stores: [],
      sales: [],
    });
    vi.mocked(listUsers).mockResolvedValue([]);
    vi.mocked(getCollaboratorSalesReport).mockResolvedValue({
      collaboratorUserId: 7,
      collaboratorName: "PKMSTORE",
      dateFrom: "2026-05-01",
      dateTo: "2026-05-05",
      totalAmount: 0,
      totalCommissionAmount: 0,
      totalNetAmount: 0,
      totalIvaAmount: 0,
      entries: [],
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
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <SalesTodayPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("Detalle mensual de ventas")).toBeInTheDocument();
    });

    const dateInputs = screen.getAllByDisplayValue("2026-05-05");
    expect(dateInputs.length).toBeGreaterThan(0);
  });
});
