import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { InventoryPage } from "@/features/inventory/pages/InventoryPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    roles: ["STORE_USER"],
    primaryRole: "STORE_USER",
  }),
}));

vi.mock("@/features/products/api/productApi", () => ({
  listProducts: vi.fn(),
}));

vi.mock("@/features/inventory/api/listStockMovements", () => ({
  listStockMovements: vi.fn(),
}));

vi.mock("@/features/inventory/api/adjustStock", () => ({
  adjustStock: vi.fn(),
}));

import { listProducts } from "@/features/products/api/productApi";

describe("InventoryPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows a read-only collaborator stock experience", async () => {
    vi.mocked(listProducts).mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
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
        <InventoryPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Mi stock")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("No hay productos disponibles")).toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { name: "Modo lectura" })).toBeInTheDocument();
    expect(screen.queryByText("Aplicar ajuste")).not.toBeInTheDocument();
  });
});
