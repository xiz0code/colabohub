import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ProductsPage } from "@/features/products/pages/ProductsPage";

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => ({
    user: {
      active: true,
      activeMarketId: 2,
      marketIds: [2],
      storeIds: [5],
      activeMarketName: "Sakura Store",
    },
  }),
}));

vi.mock("@/features/products/api/productApi", () => ({
  listProducts: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  getProductAudit: vi.fn(),
  printBarcodeLabels: vi.fn(),
  importProductsCsv: vi.fn(),
}));

vi.mock("@/features/users/api/userApi", () => ({
  listUsers: vi.fn(),
}));

import {
  createProduct,
  getProductAudit,
  importProductsCsv,
  listProducts,
  printBarcodeLabels,
  updateProduct,
} from "@/features/products/api/productApi";
import { listUsers } from "@/features/users/api/userApi";

describe("ProductsPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();

    vi.mocked(listProducts).mockResolvedValue({
      content: [
        {
          id: 10,
          storeId: 5,
          storeName: "Sakura Store",
          ownerUserId: 7,
          ownerFullName: "Camila",
          name: "Sticker BTS",
          sku: "STICKER-BTS",
          description: "Pack brillante",
          salePrice: 2000,
          cost: null,
          stock: 12,
          status: "ACTIVE",
          barcode: "7500000000100",
          hasPromotion: true,
          promotion: {
            type: "QUANTITY_BLOCK",
            quantity: 3,
            promotionalPrice: 4000,
            percentageDiscount: null,
          },
          createdAt: "2026-03-16T10:00:00Z",
          updatedAt: "2026-03-16T11:00:00Z",
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    });

    vi.mocked(listUsers).mockResolvedValue([
      {
        id: 7,
        email: "camila@example.com",
        fullName: "Camila",
        phone: null,
        contactName: null,
        description: null,
        monthlyRent: null,
        startDate: null,
        standNumber: null,
        roles: ["STORE_USER"],
        marketIds: [2],
        storeIds: [5],
        active: true,
        createdAt: "2026-03-16T10:00:00Z",
        updatedAt: "2026-03-16T10:00:00Z",
      },
    ]);

    vi.mocked(getProductAudit).mockResolvedValue([
      {
        id: 1,
        fieldName: "Precio",
        previousValue: "1000",
        newValue: "1200",
        createdAt: "2026-03-16T12:00:00Z",
        createdBy: "admin@sakura.com",
      },
    ]);

    vi.mocked(createProduct).mockResolvedValue({
      id: 11,
      storeId: 5,
      storeName: "Sakura Store",
      ownerUserId: 7,
      ownerFullName: "Camila",
      name: "Nuevo sticker",
      sku: "NUEVO-STICKER",
      description: "Desc",
      salePrice: 1500,
      cost: null,
      stock: 5,
      status: "ACTIVE",
      barcode: "7500000000199",
      hasPromotion: false,
      promotion: null,
      createdAt: "2026-03-16T10:00:00Z",
      updatedAt: "2026-03-16T10:00:00Z",
    });

    vi.mocked(updateProduct).mockResolvedValue({
      id: 10,
      storeId: 5,
      storeName: "Sakura Store",
      ownerUserId: 7,
      ownerFullName: "Camila",
      name: "Sticker BTS Editado",
      sku: "STICKER-BTS",
      description: "Desc editada",
      salePrice: 2500,
      cost: null,
      stock: 9,
      status: "ACTIVE",
      barcode: "7500000000100",
      hasPromotion: false,
      promotion: null,
      createdAt: "2026-03-16T10:00:00Z",
      updatedAt: "2026-03-16T13:00:00Z",
    });

    vi.mocked(printBarcodeLabels).mockResolvedValue(new Blob(["pdf"]));
    vi.mocked(importProductsCsv).mockResolvedValue({
      successCount: 2,
      errorCount: 1,
      errors: [
        {
          rowNumber: 3,
          rowData: "Album TXT,0,2,Precio invalido,camila@example.com,,",
          message: "El precio debe ser un numero mayor a 0.",
        },
      ],
    });
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:url"),
      revokeObjectURL: vi.fn(),
    });
  });

  it("creates products from a modal without espacio selector", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Crear producto" });
    await user.click(screen.getByRole("button", { name: "Crear producto" }));

    expect(screen.getByRole("heading", { name: "Crear producto" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Espacio")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Nombre del producto"), "Llavero TXT");
    await user.selectOptions(screen.getByLabelText("Tienda responsable"), "7");
    await user.type(screen.getByLabelText("Precio de venta"), "3500");
    await user.clear(screen.getByLabelText("Stock inicial"));
    await user.type(screen.getByLabelText("Stock inicial"), "8");
    await user.selectOptions(screen.getByLabelText("Tipo de promocion"), "QUANTITY_BLOCK");
    await user.type(screen.getByLabelText("Cantidad"), "3");
    await user.type(screen.getByLabelText("Precio promocional"), "9000");
    await user.click(screen.getByRole("button", { name: "Guardar producto" }));

    await waitFor(() => {
      expect(createProduct).toHaveBeenCalledWith(
        expect.objectContaining({
          ownerUserId: 7,
          name: "Llavero TXT",
          initialStock: 8,
          promotion: {
            type: "QUANTITY_BLOCK",
            quantity: 3,
            promotionalPrice: 9000,
          },
        }),
      );
    });
  });

  it("opens edit modal and shows audit trail", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Editar" });
    await user.click(screen.getByRole("button", { name: "Editar" }));

    expect(await screen.findByText("Historial del producto")).toBeInTheDocument();
    expect(screen.getByText(/1000/)).toBeInTheDocument();
  });

  it("opens barcode modal for selected products", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Imprimir codigos" });
    await user.click(screen.getByRole("button", { name: "Imprimir codigos" }));

    expect(screen.getByRole("heading", { name: "Imprimir codigos de barras" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("1")).toBeInTheDocument();
  });

  it("imports products from csv and shows row level results", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Carga masiva" });
    await user.click(screen.getByRole("button", { name: "Carga masiva" }));

    const input = screen.getByLabelText("Archivo CSV");
    const file = new File(
      ["nombre,precio,stock,descripcion,colaborador_email,promocion_tipo,promocion_valor\nSticker BTS,2000,6,Pack brillante,camila@example.com,,"],
      "productos.csv",
      { type: "text/csv" },
    );

    await user.upload(input, file);
    await user.click(screen.getByRole("button", { name: "Procesar archivo" }));

    await waitFor(() => {
      expect(importProductsCsv).toHaveBeenCalledWith(expect.any(File));
    });

    expect(await screen.findByText("Productos creados")).toBeInTheDocument();
    expect(screen.getByText("Fila 3")).toBeInTheDocument();
    expect(screen.getByText("El precio debe ser un numero mayor a 0.")).toBeInTheDocument();
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
      <ProductsPage />
    </QueryClientProvider>,
  );
}
