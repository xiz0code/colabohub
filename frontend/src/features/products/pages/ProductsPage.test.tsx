import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ProductsPage } from "@/features/products/pages/ProductsPage";

const sessionMock = vi.fn();

vi.mock("@/features/auth/session/SessionProvider", () => ({
  useSession: () => sessionMock(),
}));

vi.mock("@/features/products/api/productApi", () => ({
  listProducts: vi.fn(),
  listPromotionGroups: vi.fn(),
  deletePromotionGroup: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  updateProductStatus: vi.fn(),
  increaseProductStock: vi.fn(),
  getProductAudit: vi.fn(),
  printBarcodeLabels: vi.fn(),
  importProductsCsv: vi.fn(),
  importStockReductionsCsv: vi.fn(),
}));

vi.mock("@/features/users/api/userApi", () => ({
  listUsers: vi.fn(),
}));

import {
  createProduct,
  getProductAudit,
  increaseProductStock,
  importProductsCsv,
  importStockReductionsCsv,
  listProducts,
  listPromotionGroups,
  printBarcodeLabels,
  updateProductStatus,
  updateProduct,
} from "@/features/products/api/productApi";
import { listUsers } from "@/features/users/api/userApi";

describe("ProductsPage", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    sessionMock.mockReturnValue({
      primaryRole: "ADMIN_MARKET",
      user: {
        active: true,
        activeMarketId: 2,
        marketIds: [2],
        storeIds: [5],
        activeMarketName: "Sakura Store",
      },
    });

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
        factura: false,
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
    vi.mocked(listPromotionGroups).mockResolvedValue([]);
    vi.mocked(updateProductStatus).mockResolvedValue({
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
      status: "INACTIVE",
      barcode: "7500000000100",
      hasPromotion: true,
      promotion: null,
      createdAt: "2026-03-16T10:00:00Z",
      updatedAt: "2026-03-16T11:00:00Z",
    });
    vi.mocked(increaseProductStock).mockResolvedValue({
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
      stock: 18,
      status: "ACTIVE",
      barcode: "7500000000100",
      hasPromotion: true,
      promotion: null,
      createdAt: "2026-03-16T10:00:00Z",
      updatedAt: "2026-03-16T11:00:00Z",
    });
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
    vi.mocked(importStockReductionsCsv).mockResolvedValue({
      successCount: 1,
      errorCount: 0,
      errors: [],
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

  it("opens quick stock increase and sends the quantity", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Aumentar stock" });
    await user.click(screen.getByRole("button", { name: "Aumentar stock" }));
    await user.clear(screen.getByLabelText("Cuantas unidades quieres agregar"));
    await user.type(screen.getByLabelText("Cuantas unidades quieres agregar"), "6");
    await user.click(screen.getByRole("button", { name: "Agregar unidades" }));

    await waitFor(() => {
      expect(increaseProductStock).toHaveBeenCalledWith(10, 6);
    });
  });

  it("opens barcode modal for selected products", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Imprimir codigos" });
    await user.click(screen.getByRole("button", { name: "Imprimir codigos" }));

    expect(screen.getByRole("heading", { name: "Imprimir codigos de barras" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("12")).toBeInTheDocument();
    expect(screen.getByLabelText("Formato de impresion")).toHaveValue("A4");
  });

  it("sends the selected barcode print format", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Imprimir codigos" });
    await user.click(screen.getByRole("button", { name: "Imprimir codigos" }));
    await user.selectOptions(screen.getByLabelText("Formato de impresion"), "LABEL_30X20");
    await user.click(screen.getByRole("button", { name: "Descargar PDF" }));

    await waitFor(() => {
      expect(printBarcodeLabels).toHaveBeenCalledWith(
        expect.objectContaining({
          format: "LABEL_30X20",
          items: [{ productId: 10, quantity: 12 }],
        }),
      );
    });
  });

  it("splits massive barcode downloads into multiple pdf files", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("button", { name: "Imprimir codigos" });
    await user.click(screen.getByRole("button", { name: "Imprimir codigos" }));
    await user.clear(screen.getByLabelText("Cantidad"));
    await user.type(screen.getByLabelText("Cantidad"), "500");
    await user.click(screen.getByRole("button", { name: "Descargar PDF" }));

    await waitFor(() => {
      expect(printBarcodeLabels).toHaveBeenCalledTimes(3);
    });

    expect(printBarcodeLabels).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        items: [{ productId: 10, quantity: 240 }],
      }),
    );
    expect(printBarcodeLabels).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        items: [{ productId: 10, quantity: 240 }],
      }),
    );
    expect(printBarcodeLabels).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        items: [{ productId: 10, quantity: 20 }],
      }),
    );
  });

  it("paginates the product catalog when there are more than 100 products", async () => {
    const user = userEvent.setup();
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
          hasPromotion: false,
          promotion: null,
          createdAt: "2026-03-16T10:00:00Z",
          updatedAt: "2026-03-16T11:00:00Z",
        },
      ],
      page: 0,
      size: 100,
      totalElements: 125,
      totalPages: 2,
      first: true,
      last: false,
      empty: false,
    });

    renderPage();

    expect(await screen.findByText(/Mostrando página/)).toBeInTheDocument();
    expect(screen.getByText("125")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Siguiente" }));

    await waitFor(() => {
      expect(listProducts).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 1,
          size: 100,
        }),
      );
    });
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

    expect(await screen.findByText("Productos procesados")).toBeInTheDocument();
    expect(screen.getByText("Fila 3")).toBeInTheDocument();
    expect(screen.getByText("El precio debe ser un numero mayor a 0.")).toBeInTheDocument();
  });

  it("shows a read-only stock view for sellers", async () => {
    sessionMock.mockReturnValue({
      primaryRole: "SELLER",
      user: {
        active: true,
        activeMarketId: 2,
        marketIds: [2],
        storeIds: [],
        activeMarketName: "Sakura Store",
      },
    });
    vi.mocked(listProducts).mockResolvedValue({
      content: [
        {
          id: 10,
          ownerFullName: "Camila",
          name: "Sticker BTS",
          sku: "STICKER-BTS",
          description: "Pack brillante",
          price: 2000,
          salePrice: 0,
          stock: 12,
          hasPromotion: true,
          available: true,
        },
        {
          id: 11,
          ownerFullName: "PKMSTORE",
          name: "Album Kpop",
          sku: "ALBUM-KPOP",
          description: "Edicion limitada",
          price: 12000,
          salePrice: 0,
          stock: 2,
          hasPromotion: false,
          available: true,
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

    renderPage();

    expect(await screen.findByText("Consulta los productos disponibles de tu Espacio y revisa su stock sin permisos de edicion.")).toBeInTheDocument();
    expect(await screen.findByText("Sticker BTS")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Crear producto" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Carga masiva" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("$2.000")).toBeInTheDocument();
    expect(screen.getByText("Productos con stock bajo")).toBeInTheDocument();
    expect(screen.getAllByText("Album Kpop").length).toBeGreaterThan(0);
    expect(screen.getByText("1 producto(s) en alerta")).toBeInTheDocument();
  });

  it("lets a tienda create products and print barcode labels", async () => {
    sessionMock.mockReturnValue({
      primaryRole: "STORE_USER",
      user: {
        id: 7,
        fullName: "PKMSTORE",
        active: true,
        activeMarketId: 2,
        marketIds: [2],
        storeIds: [5],
        activeMarketName: "Fast And Near",
      },
    });

    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Mi stock")).toBeInTheDocument();
    expect(screen.getByText("Catalogo activo")).toBeInTheDocument();
    expect(screen.getAllByText("PKMSTORE").length).toBeGreaterThan(0);
    expect(await screen.findByText("Sticker BTS")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Agregar producto" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Carga masiva" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Historial" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Agregar producto" }));
    expect(await screen.findByRole("heading", { name: "Agregar producto" })).toBeInTheDocument();
    expect(screen.getByLabelText("Esta Tienda")).toBeDisabled();

    await user.type(screen.getByLabelText("Nombre del producto"), "Album nuevo");
    await user.type(screen.getByLabelText("Precio de venta"), "12000");
    await user.clear(screen.getByLabelText("Stock inicial"));
    await user.type(screen.getByLabelText("Stock inicial"), "10");
    await user.click(screen.getByRole("button", { name: "Guardar producto" }));

    await waitFor(() => {
      expect(createProduct).toHaveBeenCalledWith(
        expect.objectContaining({
          ownerUserId: 7,
          name: "Album nuevo",
          initialStock: 10,
        }),
      );
    });

    await user.click(screen.getByRole("button", { name: "Imprimir codigos" }));
    expect(await screen.findByRole("heading", { name: "Imprimir codigos de barras" })).toBeInTheDocument();
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
