import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { CommissionsPage } from "@/features/commissions/pages/CommissionsPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    primaryRole: "ADMIN_MARKET",
    user: {
      active: true,
      activeMarketId: 2,
      marketIds: [2],
      activeMarketName: "Sakura Store",
      storeIds: [],
    },
  }),
}));

vi.mock("@/features/commissions/api/settingsApi", () => ({
  getGlobalFinancialSettings: vi.fn(),
  getMarketFinancialSettings: vi.fn(),
  updateGlobalCommissionSettings: vi.fn(),
  updateGlobalUfValue: vi.fn(),
  updateMarketCommissionSettings: vi.fn(),
  updateMarketUfValue: vi.fn(),
  resetMarketUfValueToAutomatic: vi.fn(),
}));

vi.mock("@/features/markets/api/marketApi", () => ({
  listMarkets: vi.fn(),
}));

import {
  getMarketFinancialSettings,
  resetMarketUfValueToAutomatic,
  updateMarketCommissionSettings,
  updateMarketUfValue,
} from "@/features/commissions/api/settingsApi";

describe("CommissionsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(getMarketFinancialSettings).mockResolvedValue({
      marketId: 2,
      marketName: "Sakura Store",
      ufValue: 36500,
      ufUpdatedAt: "2026-03-16T18:00:00.000Z",
      ufManualOverride: true,
      useDynamicFixedCommission: true,
      overrideEnabled: true,
      globalCommissionUfValue: 0.00169,
      globalCommissionPercentageValue: 0.0079,
      effectiveCommissionUfValue: 0.002,
      effectiveCommissionPercentageValue: 0.009,
      globalPromotionEnabled: true,
      globalPromotionPercentage: 25,
      lowStockAlertThreshold: 2,
    });

    vi.mocked(updateMarketCommissionSettings).mockResolvedValue({
      marketId: 2,
      marketName: "Sakura Store",
      ufValue: 36500,
      ufUpdatedAt: "2026-03-16T18:00:00.000Z",
      ufManualOverride: true,
      useDynamicFixedCommission: true,
      overrideEnabled: true,
      globalCommissionUfValue: 0.00169,
      globalCommissionPercentageValue: 0.0079,
      effectiveCommissionUfValue: 0.002,
      effectiveCommissionPercentageValue: 0.009,
      globalPromotionEnabled: true,
      globalPromotionPercentage: 30,
      lowStockAlertThreshold: 2,
    });

    vi.mocked(updateMarketUfValue).mockResolvedValue({
      marketId: 2,
      marketName: "Sakura Store",
      ufValue: 37000,
      ufUpdatedAt: "2026-03-16T19:00:00.000Z",
      ufManualOverride: true,
      useDynamicFixedCommission: true,
      overrideEnabled: true,
      globalCommissionUfValue: 0.00169,
      globalCommissionPercentageValue: 0.0079,
      effectiveCommissionUfValue: 0.002,
      effectiveCommissionPercentageValue: 0.009,
      globalPromotionEnabled: true,
      globalPromotionPercentage: 25,
      lowStockAlertThreshold: 2,
    });
  });

  it("shows and updates tienda global promotion override", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Promocion global")).toBeInTheDocument();
    expect(screen.getByDisplayValue("25")).toBeInTheDocument();

    await user.clear(screen.getByLabelText("Descuento %"));
    await user.type(screen.getByLabelText("Descuento %"), "30");
    await user.click(screen.getByRole("button", { name: "Guardar promocion global" }));

    await waitFor(() => {
      expect(updateMarketCommissionSettings).toHaveBeenCalledWith(2, true, 0.002, 0.009, true, true, 30, 2);
    });
  });

  it("allows updating the tienda UF value", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Valor UF del Espacio")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Editar UF" }));
    const ufInput = screen.getAllByLabelText("Valor UF").find((input) => !input.hasAttribute("disabled"));
    expect(ufInput).toBeDefined();
    fireEvent.change(ufInput!, { target: { value: "37000" } });
    const saveButtons = screen.getAllByRole("button", { name: "Guardar UF" });
    await user.click(saveButtons[saveButtons.length - 1]);

    await waitFor(() => {
      expect(updateMarketUfValue).toHaveBeenCalledWith(37000);
    });
  });

  it("allows returning the tienda UF to automatic mode", async () => {
    vi.mocked(resetMarketUfValueToAutomatic).mockResolvedValue({
      marketId: 2,
      marketName: "Sakura Store",
      ufValue: 37100,
      ufUpdatedAt: "2026-03-16T20:00:00.000Z",
      ufManualOverride: false,
      useDynamicFixedCommission: true,
      overrideEnabled: true,
      globalCommissionUfValue: 0.00169,
      globalCommissionPercentageValue: 0.0079,
      effectiveCommissionUfValue: 0.002,
      effectiveCommissionPercentageValue: 0.009,
      globalPromotionEnabled: true,
      globalPromotionPercentage: 25,
      lowStockAlertThreshold: 2,
    });

    const user = userEvent.setup();
    renderPage();

    expect((await screen.findAllByText("Estado: Manual")).length).toBeGreaterThan(0);
    const automaticButtons = screen.getAllByRole("button", { name: "Volver a automatico" });
    await user.click(automaticButtons[automaticButtons.length - 1]);

    await waitFor(() => {
      expect(resetMarketUfValueToAutomatic).toHaveBeenCalled();
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
      <CommissionsPage />
    </QueryClientProvider>,
  );
}
