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
}));

vi.mock("@/features/users/api/userApi", () => ({
  listUsers: vi.fn(),
}));

import { getCollaboratorSalesReport, getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { listUsers } from "@/features/users/api/userApi";

describe("SalesTodayPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSession.mockReturnValue({
      primaryRole: "STORE_USER",
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
      expect(screen.getByText("No tienes ventas visibles hoy")).toBeInTheDocument();
    });
    expect(screen.getByText("No hay ventas visibles para mostrar")).toBeInTheDocument();
  });

  it("renders the tienda report for admin users", async () => {
    mockUseSession.mockReturnValue({
      primaryRole: "ADMIN_SYSTEM",
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
          productName: "Sticker BTS",
          quantity: 2,
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
    expect(screen.getByText("Sticker BTS")).toBeInTheDocument();
    expect(screen.getByText("Monto acumulado")).toBeInTheDocument();
  });
});
