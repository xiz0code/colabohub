import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DailyClosingPage } from "@/features/closings/pages/DailyClosingPage";
import { ApiError } from "@/shared/lib/api/client";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    roles: ["ADMIN_SYSTEM"],
    primaryRole: "ADMIN_SYSTEM",
    user: null,
  }),
}));

vi.mock("@/features/markets/api/marketApi", () => ({
  listMarkets: vi.fn(),
}));

vi.mock("@/features/closings/api/closingApi", () => ({
  previewDailyClosing: vi.fn(),
  closeDaily: vi.fn(),
  previewMonthlyClosing: vi.fn(),
  closeMonthly: vi.fn(),
}));

vi.mock("@/shared/lib/files/downloadCsv", () => ({
  downloadCsv: vi.fn(),
}));

import { closeDaily, closeMonthly, previewDailyClosing, previewMonthlyClosing } from "@/features/closings/api/closingApi";
import { listMarkets } from "@/features/markets/api/marketApi";
import { downloadCsv } from "@/shared/lib/files/downloadCsv";

describe("DailyClosingPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the daily preview before saving the closing", async () => {
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
    vi.mocked(previewDailyClosing).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingDate: "2026-03-15",
      saleCount: 1,
      totalSalesAmount: 15000,
      totalCommissionAmount: 1000,
      totalNetAmount: 14000,
      closedAt: null,
      closedBy: "Vista previa",
      stores: [
        {
          storeId: 5,
          storeName: "PKM Store",
          saleCount: 1,
          totalSalesAmount: 15000,
          totalCommissionAmount: 1000,
          totalNetAmount: 14000,
          totalItems: 2,
        },
      ],
    });
    vi.mocked(previewMonthlyClosing).mockRejectedValue(new ApiError("Not found", 404));
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
    vi.mocked(closeMonthly).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingMonth: "2026-03",
      saleCount: 0,
      totalSalesAmount: 0,
      totalCommissionAmount: 0,
      totalNetAmount: 0,
      totalIvaAmount: 0,
      totalIvaToPayAmount: 0,
      closedAt: "2026-03-15T00:00:00Z",
      closedBy: "system",
      collaborators: [],
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
          <DailyClosingPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Espacio"), "1");

    await waitFor(() => {
      expect(screen.getByText("Informe preliminar")).toBeInTheDocument();
      expect(screen.getAllByText("PKM Store").length).toBeGreaterThan(0);
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
    vi.mocked(previewDailyClosing).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingDate: "2026-03-15",
      saleCount: 0,
      totalSalesAmount: 0,
      totalCommissionAmount: 0,
      totalNetAmount: 0,
      closedAt: null,
      closedBy: "Vista previa",
      stores: [],
    });
    vi.mocked(previewMonthlyClosing).mockRejectedValue(new ApiError("Not found", 404));

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
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <DailyClosingPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Espacio"), "1");

    await waitFor(() => {
      expect(screen.getByText("Informe preliminar")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Guardar cierre diario" }));
    expect(screen.getByRole("heading", { name: "Confirmar cierre diario" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirmar cierre diario" }));

    expect(screen.getByRole("button", { name: "Guardando..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Actualizar vista previa" })).toBeDisabled();

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
      expect(screen.getByText("Cierre diario guardado y actualizado correctamente.")).toBeInTheDocument();
    });
  });

  it("shows the monthly preview before saving the closing", async () => {
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
    vi.mocked(previewDailyClosing).mockRejectedValue(new ApiError("Not found", 404));
    vi.mocked(previewMonthlyClosing).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingMonth: "2026-03",
      saleCount: 3,
      totalSalesAmount: 30000,
      totalCommissionAmount: 3000,
      totalNetAmount: 27000,
      totalIvaAmount: 2000,
      totalIvaToPayAmount: 1000,
      closedAt: null,
      closedBy: "Vista previa",
      collaborators: [
        {
          collaboratorUserId: 5,
          collaboratorName: "PKM Store",
          collaboratorEmail: "pkm@store.cl",
          factura: false,
          saleCount: 3,
          totalItems: 4,
          totalSalesAmount: 30000,
          totalCommissionAmount: 3000,
          totalNetAmount: 27000,
          totalIvaAmount: 2000,
          ivaToPayAmount: 1000,
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
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <DailyClosingPage defaultMode="monthly" />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Espacio"), "1");

    await waitFor(() => {
      expect(screen.getByText("Informe preliminar")).toBeInTheDocument();
      expect(screen.getByText(/Vista previa sin guardar/)).toBeInTheDocument();
    });
  });

  it("requires confirmation before generating the monthly closing", async () => {
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
    vi.mocked(previewDailyClosing).mockRejectedValue(new ApiError("Not found", 404));
    vi.mocked(previewMonthlyClosing).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingMonth: "2026-03",
      saleCount: 5,
      totalSalesAmount: 125000,
      totalCommissionAmount: 18000,
      totalNetAmount: 107000,
      totalIvaAmount: 9500,
      totalIvaToPayAmount: 5000,
      closedAt: null,
      closedBy: "Vista previa",
      collaborators: [],
    });
    vi.mocked(closeMonthly).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingMonth: "2026-03",
      saleCount: 5,
      totalSalesAmount: 125000,
      totalCommissionAmount: 18000,
      totalNetAmount: 107000,
      totalIvaAmount: 9500,
      totalIvaToPayAmount: 5000,
      closedAt: "2026-03-15T00:00:00Z",
      closedBy: "system",
      collaborators: [],
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
          <DailyClosingPage defaultMode="monthly" />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Espacio"), "1");
    await user.click(screen.getByRole("button", { name: "Guardar cierre mensual" }));

    expect(screen.getByRole("heading", { name: "Confirmar cierre mensual" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirmar cierre mensual" }));

    await waitFor(() => {
      expect(closeMonthly).toHaveBeenCalled();
    });
  });

  it("downloads the daily closing as csv", async () => {
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
    vi.mocked(previewDailyClosing).mockResolvedValue({
      marketId: 1,
      marketName: "Mercado Creativo",
      closingDate: "2026-03-15",
      saleCount: 2,
      totalSalesAmount: 45000,
      totalCommissionAmount: 3000,
      totalNetAmount: 42000,
      closedAt: "2026-03-15T21:00:00Z",
      closedBy: "Karina",
      stores: [
        {
          storeId: 5,
          storeName: "PKM Store",
          saleCount: 2,
          totalSalesAmount: 45000,
          totalCommissionAmount: 3000,
          totalNetAmount: 42000,
          totalItems: 3,
        },
      ],
    });
    vi.mocked(previewMonthlyClosing).mockRejectedValue(new ApiError("Not found", 404));

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
          <DailyClosingPage />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole("option", { name: "Mercado Creativo" })).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("Espacio"), "1");

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Descargar CSV" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Descargar CSV" }));

    expect(downloadCsv).toHaveBeenCalledWith(
      "cierre-diario-mercado-creativo-2026-03-15.csv",
      expect.arrayContaining([
        expect.objectContaining({
          tienda: "PKM Store",
          ventas: 2,
          items: 3,
        }),
      ]),
    );
  });
});
