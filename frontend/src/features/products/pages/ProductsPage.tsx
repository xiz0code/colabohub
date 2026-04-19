import { FormEvent, type Dispatch, type SetStateAction, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { getMarketFinancialSettings } from "@/features/commissions/api/settingsApi";
import {
  createProduct,
  deletePromotionGroup,
  getProductAudit,
  importProductsCsv,
  importStockReductionsCsv,
  listPromotionGroups,
  listProducts,
  printBarcodeLabels,
  type Product,
  type ProductAuditLog,
  type ProductImportResult,
  type ProductPromotionGroup,
  type ProductPromotion,
  type ProductPromotionInput,
  updateProduct,
  updateProductStatus,
} from "@/features/products/api/productApi";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type ProductFormState = {
  name: string;
  ownerUserId: string;
  salePrice: string;
  stock: string;
  description: string;
  promotionGroupName: string;
  promotionType: "NONE" | "QUANTITY_BLOCK" | "PERCENTAGE_DISCOUNT" | "PAYMENT_METHOD_DISCOUNT";
  promotionQuantity: string;
  promotionPrice: string;
  promotionPercentage: string;
  promotionAppliesToCash: boolean;
  promotionAppliesToDebit: boolean;
  promotionEndsAt: string;
};

type BarcodeModalState = {
  items: Array<{ productId: number; productName: string; quantity: string }>;
};

type ImportModalState = {
  file: File | null;
  result: ProductImportResult | null;
};

type StockReductionModalState = {
  file: File | null;
  result: ProductImportResult | null;
};

const EMPTY_FORM: ProductFormState = {
  name: "",
  ownerUserId: "",
  salePrice: "",
  stock: "0",
  description: "",
  promotionGroupName: "",
  promotionType: "NONE",
  promotionQuantity: "",
  promotionPrice: "",
  promotionPercentage: "",
  promotionAppliesToCash: true,
  promotionAppliesToDebit: true,
  promotionEndsAt: "",
};

export function ProductsPage() {
  const queryClient = useQueryClient();
  const { user, primaryRole } = useSession();
  const activeMarketName = user?.activeMarketName ?? "tu Espacio";
  const activeMarketId = user?.activeMarketId ?? null;
  const currentUserId = user?.id ?? null;
  const isSeller = primaryRole === "SELLER";
  const isStoreUser = primaryRole === "STORE_USER";
  const canImport = !isSeller && !isStoreUser;
  const canImportStockReductions = primaryRole === "ADMIN_SYSTEM" || primaryRole === "ADMIN_MARKET" || primaryRole === "COLLABORATOR";
  const canCreateProducts = !isSeller;
  const canEditProducts = primaryRole === "ADMIN_SYSTEM" || primaryRole === "ADMIN_MARKET";
  const canDeleteProducts = primaryRole === "ADMIN_SYSTEM" || primaryRole === "ADMIN_MARKET";
  const canPrintBarcodes = !isSeller;
  const canViewHistory = !isSeller;
  const [stockViewFilter, setStockViewFilter] = useState<"ALL" | "LOW_STOCK" | "WITH_PROMOTION" | "NO_PROMOTION">("ALL");
  const [lowStockThreshold, setLowStockThreshold] = useState("2");
  const [search, setSearch] = useState("");
  const [selectedOwnerId, setSelectedOwnerId] = useState("");
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editProduct, setEditProduct] = useState<Product | null>(null);
  const [historyProduct, setHistoryProduct] = useState<Product | null>(null);
  const [productToDelete, setProductToDelete] = useState<Product | null>(null);
  const [createForm, setCreateForm] = useState<ProductFormState>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<ProductFormState>(EMPTY_FORM);
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>([]);
  const [barcodeModal, setBarcodeModal] = useState<BarcodeModalState | null>(null);
  const [importModal, setImportModal] = useState<ImportModalState | null>(null);
  const [stockReductionModal, setStockReductionModal] = useState<StockReductionModalState | null>(null);

  const marketSettingsQuery = useQuery({
    queryKey: ["settings", "market", activeMarketId],
    queryFn: () => getMarketFinancialSettings(activeMarketId!),
    enabled: activeMarketId !== null,
  });

  const productsQuery = useQuery({
    queryKey: ["products", "catalog", search, selectedOwnerId],
    queryFn: () =>
      listProducts({
        size: 500,
        ownerUserId: selectedOwnerId ? Number(selectedOwnerId) : undefined,
        query: search || undefined,
        status: "ACTIVE",
      }),
  });

  const usersQuery = useQuery({
    queryKey: ["users", "catalog"],
    queryFn: listUsers,
    enabled: !isSeller && !isStoreUser,
  });

  const collaborators = useMemo(
    () => {
      if (isStoreUser && currentUserId && user?.fullName) {
        return [{ id: currentUserId, fullName: user.fullName }];
      }

      return (usersQuery.data ?? []).filter((candidate) => {
        if (!candidate.active || !candidate.roles.includes("STORE_USER")) {
          return false;
        }
        return activeMarketId ? candidate.marketIds.includes(activeMarketId) || candidate.storeIds.length > 0 : true;
      });
    },
    [activeMarketId, currentUserId, isStoreUser, user?.fullName, usersQuery.data],
  );
  const tiendaOptions = useMemo(
    () =>
      collaborators
        .map((collaborator) => ({ value: String(collaborator.id), label: collaborator.fullName }))
        .sort((left, right) => left.label.localeCompare(right.label)),
    [collaborators],
  );
  const createOwnerUserId = createForm.ownerUserId ? Number(createForm.ownerUserId) : undefined;
  const editOwnerUserId = editForm.ownerUserId ? Number(editForm.ownerUserId) : undefined;
  const createPromotionGroupsQuery = useQuery({
    queryKey: ["products", "promotion-groups", "create", createOwnerUserId],
    queryFn: () => listPromotionGroups({ ownerUserId: createOwnerUserId }),
    enabled: !isSeller && Boolean(createOwnerUserId),
  });
  const editPromotionGroupsQuery = useQuery({
    queryKey: ["products", "promotion-groups", "edit", editOwnerUserId],
    queryFn: () => listPromotionGroups({ ownerUserId: editOwnerUserId }),
    enabled: !isSeller && Boolean(editOwnerUserId),
  });
  const resolvedLowStockThreshold = useMemo(() => {
    const parsed = Number(lowStockThreshold);
    if (Number.isFinite(parsed) && parsed >= 0) {
      return parsed;
    }
    return marketSettingsQuery.data?.lowStockAlertThreshold ?? 2;
  }, [lowStockThreshold, marketSettingsQuery.data?.lowStockAlertThreshold]);

  useEffect(() => {
    if (!marketSettingsQuery.data) {
      return;
    }
    setLowStockThreshold(String(marketSettingsQuery.data.lowStockAlertThreshold ?? 2));
  }, [marketSettingsQuery.data]);
  const filteredProducts = useMemo(
    () => {
      const ownerFiltered = productsQuery.data?.content ?? [];

      switch (stockViewFilter) {
        case "LOW_STOCK":
          return ownerFiltered.filter((product) => product.stock <= resolvedLowStockThreshold);
        case "WITH_PROMOTION":
          return ownerFiltered.filter((product) => Boolean(product.hasPromotion));
        case "NO_PROMOTION":
          return ownerFiltered.filter((product) => !product.hasPromotion);
        default:
          return ownerFiltered;
      }
    },
    [productsQuery.data, resolvedLowStockThreshold, selectedOwnerId, stockViewFilter],
  );

  const selectedProducts = useMemo(
    () => filteredProducts.filter((product) => selectedProductIds.includes(product.id)),
    [filteredProducts, selectedProductIds],
  );
  const lowStockProducts = useMemo(
    () =>
      (productsQuery.data?.content ?? [])
        .filter((product) => product.stock <= resolvedLowStockThreshold)
        .sort((left, right) => left.stock - right.stock || left.name.localeCompare(right.name)),
    [productsQuery.data, resolvedLowStockThreshold],
  );
  const highestStockAlert = lowStockProducts.reduce((max, product) => Math.max(max, product.stock), 0);

  const auditQuery = useQuery({
    queryKey: ["products", "audit", editProduct?.id ?? historyProduct?.id],
    queryFn: () => getProductAudit((editProduct ?? historyProduct)!.id),
    enabled: editProduct !== null || historyProduct !== null,
  });

  useEffect(() => {
    if (editProduct) {
      const nextForm = toFormState(editProduct);
      if (isStoreUser && currentUserId) {
        nextForm.ownerUserId = String(currentUserId);
      }
      setEditForm(nextForm);
    }
  }, [currentUserId, editProduct, isStoreUser]);

  useEffect(() => {
    setSelectedProductIds((current) =>
      current.filter((id) => filteredProducts.some((product) => product.id === id)),
    );
  }, [filteredProducts]);

  const createMutation = useMutation({
    mutationFn: () =>
      createProduct({
        ownerUserId: Number(createForm.ownerUserId),
        name: createForm.name.trim(),
        sku: buildSku(createForm.name),
        promotionGroupName: createForm.promotionGroupName.trim() || undefined,
        description: createForm.description.trim() || undefined,
        salePrice: Number(createForm.salePrice),
        initialStock: Number(createForm.stock),
        promotion: buildPromotionInput(createForm),
      }),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Producto creado correctamente." });
      setCreateOpen(false);
      setCreateForm(EMPTY_FORM);
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos crear el producto.") }),
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      updateProduct(editProduct!.id, {
        ownerUserId: Number(editForm.ownerUserId),
        name: editForm.name.trim(),
        sku: editProduct!.sku,
        promotionGroupName: editForm.promotionGroupName.trim() || undefined,
        description: editForm.description.trim() || undefined,
        salePrice: Number(editForm.salePrice),
        stock: Number(editForm.stock),
        promotion: buildPromotionInput(editForm),
      }),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Producto actualizado correctamente." });
      setEditProduct(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "audit"] }),
      ]);
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos actualizar el producto.") }),
  });

  const deleteMutation = useMutation({
    mutationFn: (product: Product) => updateProductStatus(product.id, "INACTIVE"),
    onSuccess: async (_, product) => {
      setFeedback({ kind: "success", message: `${product.name} fue eliminado del catalogo activo.` });
      setProductToDelete(null);
      setSelectedProductIds((current) => current.filter((id) => id !== product.id));
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos eliminar el producto.") }),
  });

  const deletePromotionGroupMutation = useMutation({
    mutationFn: (group: ProductPromotionGroup) => deletePromotionGroup(group.id),
    onSuccess: async (_, group) => {
      setFeedback({ kind: "success", message: `Grupo promocional ${group.name} eliminado.` });
      await queryClient.invalidateQueries({ queryKey: ["products", "promotion-groups"] });
    },
    onError: (error) =>
      setFeedback({
        kind: "error",
        message: getErrorMessage(error, "No pudimos eliminar el grupo promocional. Revisa que no tenga productos asociados."),
      }),
  });

  const barcodeMutation = useMutation({
    mutationFn: () =>
      printBarcodeLabels({
        items: (barcodeModal?.items ?? []).map((item) => ({
          productId: item.productId,
          quantity: Number(item.quantity),
        })),
        includeCollaboratorName: true,
      }),
    onSuccess: async (blob) => {
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "codigos-colabohub.pdf";
      anchor.click();
      window.URL.revokeObjectURL(url);
      setBarcodeModal(null);
      setFeedback({ kind: "success", message: "PDF de codigos generado correctamente." });
      await Promise.resolve();
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos generar el PDF.") }),
  });

  const importMutation = useMutation({
    mutationFn: (file: File) => importProductsCsv(file),
    onSuccess: async (result) => {
      setImportModal((current) => (current ? { ...current, result } : { file: null, result }));
      setFeedback({
        kind: result.errorCount > 0 ? "info" : "success",
        message:
          result.errorCount > 0
            ? `La carga masiva termino con ${result.successCount} productos creados y ${result.errorCount} filas con observaciones.`
            : `Se importaron ${result.successCount} productos correctamente.`,
      });
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos procesar el archivo CSV.") }),
  });

  const stockReductionMutation = useMutation({
    mutationFn: (file: File) => importStockReductionsCsv(file),
    onSuccess: async (result) => {
      setStockReductionModal((current) => (current ? { ...current, result } : { file: null, result }));
      setFeedback({
        kind: result.errorCount > 0 ? "info" : "success",
        message:
          result.errorCount > 0
            ? `La reduccion masiva termino con ${result.successCount} productos ajustados y ${result.errorCount} filas con observaciones.`
            : `Se redujo el stock de ${result.successCount} producto(s) correctamente.`,
      });
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos procesar la reduccion de stock.") }),
  });

  const allVisibleSelected =
    filteredProducts.length > 0 &&
    filteredProducts.every((product) => selectedProductIds.includes(product.id));

  return (
    <section className="space-y-6">
      <PageHeader
        title={isStoreUser ? "Mi stock" : "Stock"}
        description={
          isSeller
            ? "Consulta los productos disponibles de tu Espacio y revisa su stock sin permisos de edicion."
            : isStoreUser
              ? "Carga productos nuevos, imprime codigos de barra y revisa tu catalogo en una sola vista."
              : "Gestiona productos, promociones y codigos de barra con una vista amplia y comoda para operar tu Espacio."
        }
        eyebrow={isStoreUser ? "Operacion diaria" : "Catalogo principal"}
      />

      {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

      <div className="soft-surface p-6">
        <div className="grid gap-5">
          <div className="rounded-[28px] border border-white/75 bg-[linear-gradient(135deg,rgba(255,255,255,0.92),rgba(255,247,252,0.88))] p-5 shadow-[0_16px_36px_rgba(186,170,211,0.08)]">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-violet-500">Catalogo activo</p>
            <h2 className="mt-2 text-2xl font-black tracking-tight text-slate-900">{isStoreUser ? user?.fullName ?? "Tu Tienda" : activeMarketName}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {isStoreUser
                ? "Administra tus productos, busca por nombre, SKU o codigo de barras y prepara etiquetas para operar con rapidez."
              : "Cada producto se vincula a una Tienda y puede encontrarse por nombre, SKU o codigo de barras."}
            </p>
          </div>

          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-[minmax(220px,0.85fr)_minmax(360px,1.55fr)_auto_auto_auto] lg:items-stretch">
            {!isSeller ? (
              <select
                value={selectedOwnerId}
                onChange={(event) => setSelectedOwnerId(event.target.value)}
                className="min-h-12 rounded-[20px] border border-white/85 bg-white/80 px-4 py-3 text-sm shadow-sm outline-none transition focus:border-violet-200 focus:bg-white"
              >
                <option value="">Todas las Tiendas</option>
                {tiendaOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : null}
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por nombre, SKU o codigo de barras"
              aria-label="Buscar por nombre, SKU o codigo de barras"
              className="min-h-12 rounded-[20px] border border-white/85 bg-white/80 px-5 py-3 text-sm shadow-sm outline-none transition focus:border-violet-200 focus:bg-white"
            />
            {canImport ? (
              <button
                type="button"
                onClick={() => setImportModal({ file: null, result: null })}
                className="min-h-12 whitespace-nowrap rounded-[20px] border border-white/90 bg-white/85 px-6 py-3 text-sm font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
              >
                Carga masiva
              </button>
            ) : null}
            {canImportStockReductions ? (
              <button
                type="button"
                onClick={() => setStockReductionModal({ file: null, result: null })}
                className="min-h-12 whitespace-nowrap rounded-[20px] border border-amber-100 bg-amber-50/90 px-6 py-3 text-sm font-semibold text-amber-800 shadow-sm transition hover:-translate-y-0.5"
              >
                Reducir stock
              </button>
            ) : null}
            {canCreateProducts ? (
              <button
                type="button"
                onClick={() => {
                  setCreateForm(
                    isStoreUser && currentUserId
                      ? { ...EMPTY_FORM, ownerUserId: String(currentUserId) }
                      : EMPTY_FORM,
                  );
                  setCreateOpen(true);
                }}
                disabled={collaborators.length === 0}
                className="min-h-12 whitespace-nowrap rounded-[20px] bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-6 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isStoreUser ? "Agregar producto" : "Crear producto"}
              </button>
            ) : null}
          </div>
        </div>

        {canCreateProducts && collaborators.length === 0 ? (
          <div className="mt-5">
            <FeedbackMessage kind="info" message="Necesitas al menos una Tienda activa para asignar productos al catalogo." />
          </div>
        ) : null}

        <div className="mt-6 flex flex-col gap-3 rounded-[24px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.94),rgba(255,247,251,0.92))] p-4 shadow-[0_16px_40px_rgba(186,170,211,0.08)] lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap gap-2">
            <FilterChip label="Todos" active={stockViewFilter === "ALL"} onClick={() => setStockViewFilter("ALL")} />
            <FilterChip label="Stock bajo" active={stockViewFilter === "LOW_STOCK"} onClick={() => setStockViewFilter("LOW_STOCK")} />
            <FilterChip label="Con promocion" active={stockViewFilter === "WITH_PROMOTION"} onClick={() => setStockViewFilter("WITH_PROMOTION")} />
            <FilterChip label="Sin promocion" active={stockViewFilter === "NO_PROMOTION"} onClick={() => setStockViewFilter("NO_PROMOTION")} />
          </div>

          <label className="flex items-center gap-3 text-sm text-muted-foreground">
            <span>Stock bajo desde</span>
            <select
              value={lowStockThreshold}
              onChange={(event) => setLowStockThreshold(event.target.value)}
              className="rounded-full border border-white/85 bg-white/80 px-4 py-2.5 text-sm font-semibold text-foreground shadow-sm outline-none transition focus:border-violet-200 focus:bg-white"
            >
              <option value="1">1 unidad</option>
              <option value="2">2 unidades</option>
              <option value="3">3 unidades</option>
              <option value="5">5 unidades</option>
            </select>
          </label>
        </div>

        {isStoreUser || isSeller ? (
          <div className="mt-6 rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,247,251,0.94))] p-5 shadow-[0_16px_40px_rgba(186,170,211,0.08)]">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <div>
                <h3 className="text-lg font-semibold">Productos con stock bajo</h3>
                <p className="text-sm text-muted-foreground">
                  Alerta visual de productos que estan en {resolvedLowStockThreshold} unidades o menos para que puedas reponer a tiempo.
                </p>
              </div>
              <span className="soft-chip">{lowStockProducts.length} producto(s) en alerta</span>
            </div>

            {lowStockProducts.length === 0 ? (
              <div className="mt-4">
                <EmptyState
                  title="No tienes productos con stock bajo"
                  description={`Cuando un producto llegue a ${resolvedLowStockThreshold} unidades o menos, aparecera aqui con prioridad visual.`}
                />
              </div>
            ) : (
              <div className="mt-5 grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                <div className="space-y-3">
                  {lowStockProducts.map((product) => (
                    <LowStockBar
                      key={product.id}
                      label={product.name}
                      stock={product.stock}
                      maxStock={Math.max(highestStockAlert, resolvedLowStockThreshold)}
                    />
                  ))}
                </div>

                <div className="soft-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Producto</th>
                        <th>SKU</th>
                        <th>Stock</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {lowStockProducts.map((product) => (
                        <tr key={`low-${product.id}`}>
                          <td className="font-medium">{product.name}</td>
                          <td>{product.sku}</td>
                          <td>{product.stock}</td>
                          <td>
                            <span className="soft-chip text-rose-700">
                              {product.stock <= 1 ? "Critico" : "Bajo"}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        ) : null}

        <div className="mt-6 flex flex-col gap-3 rounded-[24px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.94),rgba(255,247,251,0.92))] p-4 shadow-[0_16px_40px_rgba(186,170,211,0.08)] sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className="soft-chip">{filteredProducts.length} productos visibles</span>
            {!selectedOwnerId && productsQuery.data ? (
              <span className="soft-chip">{productsQuery.data.totalElements} totales</span>
            ) : null}
            {!isSeller ? <span className="soft-chip">{selectedProductIds.length} seleccionados</span> : null}
          </div>
          {canPrintBarcodes ? (
            <button
              type="button"
              onClick={() =>
                setBarcodeModal({
                  items: selectedProducts.map((product) => ({
                    productId: product.id,
                    productName: product.name,
                    quantity: "1",
                  })),
                })
              }
              disabled={selectedProducts.length === 0}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Imprimir codigos seleccionados
            </button>
          ) : null}
        </div>

        {productsQuery.isLoading ? <div className="mt-6"><FeedbackMessage kind="info" message="Cargando catalogo..." /></div> : null}
        {productsQuery.isError ? (
          <div className="mt-6">
            <FeedbackMessage kind="error" message={getErrorMessage(productsQuery.error, "No pudimos cargar el catalogo.")} />
          </div>
        ) : null}

        {!productsQuery.isLoading && filteredProducts.length === 0 ? (
          <div className="mt-6">
            <EmptyState
              title={selectedOwnerId ? "No hay productos para esa Tienda" : "Todavia no hay productos en tu Espacio"}
              description={
                isStoreUser
                  ? "Agrega tu primer producto y deja tus etiquetas listas para imprimir."
                  : selectedOwnerId
                    ? "Prueba otra Tienda o limpia los filtros para revisar el catalogo completo."
                    : "Crea tu primer producto para empezar a vender, imprimir codigos y organizar el stock."
              }
            />
          </div>
        ) : (
          <div className="soft-table mt-6">
            <table>
              <thead>
                <tr>
                  <th className="w-[28%]">
                    {isSeller ? (
                      <span>Producto</span>
                    ) : (
                      <label className="inline-flex items-center gap-2">
                        <input
                          type="checkbox"
                          checked={allVisibleSelected}
                          onChange={() =>
                            setSelectedProductIds(
                              allVisibleSelected ? [] : filteredProducts.map((product) => product.id),
                            )
                          }
                        />
                        <span>Producto</span>
                      </label>
                    )}
                  </th>
                  <th>Tienda</th>
                  <th>Precio</th>
                  <th>Stock</th>
                  <th>Descripcion corta</th>
                  <th>Promocion</th>
          {(canEditProducts || canDeleteProducts || canViewHistory || canPrintBarcodes) ? <th>Acciones</th> : null}
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map((product) => (
                  <tr key={product.id}>
                    <td>
                      {isSeller ? (
                        <div>
                          <p className="font-semibold">{product.name}</p>
                          <p className="text-xs text-muted-foreground">SKU {product.sku}</p>
                        </div>
                      ) : (
                        <label className="flex items-start gap-3">
                          <input
                            type="checkbox"
                            checked={selectedProductIds.includes(product.id)}
                            onChange={() => toggleSelection(product.id, setSelectedProductIds)}
                          />
                          <div>
                            <p className="font-semibold">{product.name}</p>
                            <p className="text-xs text-muted-foreground">SKU {product.sku}</p>
                            {product.promotionGroupName ? (
                              <p className="mt-1 text-xs font-medium text-fuchsia-700">Grupo {product.promotionGroupName}</p>
                            ) : null}
                          </div>
                        </label>
                      )}
                    </td>
                  <td>{product.ownerFullName ?? "Sin Tienda asignada"}</td>
                    <td>{formatCurrency(resolveProductSalePrice(product))}</td>
                    <td>
                      <span className={["soft-chip", product.stock <= 5 ? "text-rose-700" : "text-emerald-700"].join(" ")}>
                        {product.stock}
                      </span>
                    </td>
                    <td>{truncate(product.description ?? "Sin descripcion", 44)}</td>
                    <td>
                      <span className={product.hasPromotion ? "soft-chip text-fuchsia-700" : "soft-chip text-muted-foreground"}>
                        {product.hasPromotion ? "SI" : "NO"}
                      </span>
                    </td>
                    {(canEditProducts || canDeleteProducts || canViewHistory || canPrintBarcodes) ? (
                      <td>
                        <div className="flex flex-wrap gap-2">
                          {canEditProducts ? (
                            <button
                              type="button"
                              onClick={() => setEditProduct(product)}
                              className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
                            >
                              Editar
                            </button>
                          ) : null}
                          {canViewHistory ? (
                            <button
                              type="button"
                              onClick={() => setHistoryProduct(product)}
                              className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
                            >
                              Historial
                            </button>
                          ) : null}
                          {canDeleteProducts ? (
                            <button
                              type="button"
                              onClick={() => setProductToDelete(product)}
                              disabled={deleteMutation.isPending}
                              className="rounded-full border border-rose-100 bg-rose-50/90 px-3 py-1.5 text-xs font-semibold text-rose-700 shadow-sm transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              Eliminar
                            </button>
                          ) : null}
                          {canPrintBarcodes ? (
                            <button
                              type="button"
                              onClick={() =>
                                setBarcodeModal({
                                  items: [{ productId: product.id, productName: product.name, quantity: "1" }],
                                })
                              }
                              className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
                            >
                              Imprimir codigos
                            </button>
                          ) : null}
                        </div>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal
        open={canDeleteProducts && productToDelete !== null}
        onClose={() => {
          if (!deleteMutation.isPending) {
            setProductToDelete(null);
          }
        }}
        title="Eliminar producto"
        description="El producto saldra del catalogo activo, pero se conservara su historial para ventas, cierres y auditoria."
        maxWidthClassName="max-w-xl"
        closeOnOverlayClick={!deleteMutation.isPending}
        closeOnEscape={!deleteMutation.isPending}
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={() => setProductToDelete(null)}
              disabled={deleteMutation.isPending}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={() => {
                if (productToDelete) {
                  deleteMutation.mutate(productToDelete);
                }
              }}
              disabled={deleteMutation.isPending || !productToDelete}
              className="rounded-full border border-rose-200 bg-[linear-gradient(135deg,rgba(255,123,156,0.96),rgba(255,169,119,0.96))] px-4 py-2.5 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(244,114,154,0.22)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {deleteMutation.isPending ? "Eliminando..." : "Eliminar producto"}
            </button>
          </div>
        }
      >
        {productToDelete ? (
          <div className="space-y-5">
            <div className="rounded-[28px] border border-rose-100 bg-[linear-gradient(135deg,rgba(255,244,247,0.98),rgba(255,249,243,0.96))] p-5">
              <p className="text-xs font-semibold uppercase tracking-[0.22em] text-rose-500">Confirmacion requerida</p>
              <h3 className="mt-3 text-2xl font-black tracking-tight text-slate-900">{productToDelete.name}</h3>
              <p className="mt-2 text-sm text-muted-foreground">
                Esta accion lo ocultara del stock activo y no podra agregarse a nuevas ventas.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <InfoCard label="Tienda" value={productToDelete.ownerFullName ?? "Sin Tienda"} />
              <InfoCard label="SKU" value={productToDelete.sku} />
              <InfoCard label="Stock actual" value={`${productToDelete.stock} unidad(es)`} />
            </div>

            <FeedbackMessage
              kind="info"
              message="No se borra el registro historico: solo se desactiva para mantener trazabilidad de ventas y cierres."
            />
          </div>
        ) : null}
      </Modal>

      <Modal
        open={canImport && importModal !== null}
        onClose={() => {
          if (!importMutation.isPending) {
            setImportModal(null);
          }
        }}
        title="Carga masiva de productos"
        description="Importa productos con una plantilla CSV y revisa con claridad cualquier fila que necesite correccion."
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={downloadTemplate}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-foreground"
            >
              Descargar plantilla
            </button>
            <button
              type="button"
              onClick={() => setImportModal(null)}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground"
            >
              Cerrar
            </button>
            <button
              type="button"
              onClick={() => {
                if (importModal?.file) {
                  setFeedback(null);
                  importMutation.mutate(importModal.file);
                }
              }}
              disabled={importMutation.isPending || !importModal?.file}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {importMutation.isPending ? "Importando..." : "Procesar archivo"}
            </button>
          </div>
        }
      >
        <div className="space-y-5">
          <div className="soft-subtle-surface p-4">
            <p className="text-sm font-semibold">Plantilla esperada</p>
            <p className="mt-1 text-sm text-muted-foreground">
              nombre,precio,stock,descripcion,colaborador_email,grupo_promocional,promocion_tipo,promocion_valor,promocion_fin
            </p>
            <p className="mt-2 text-xs text-muted-foreground">
              Tipos disponibles: CANTIDAD, PORCENTAJE o MEDIO_PAGO. Para MEDIO_PAGO usa un valor como 10%:efectivo|debito.
            </p>
          </div>

          <label className="grid gap-2 text-sm">
            <span>Archivo CSV</span>
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(event) =>
                setImportModal((current) =>
                  current
                    ? {
                        ...current,
                        file: event.target.files?.[0] ?? null,
                        result: null,
                      }
                    : current,
                )
              }
              className="rounded-2xl border border-input bg-background px-3 py-3"
            />
          </label>

          {importModal?.file ? (
            <div className="soft-chip w-fit">
              Archivo seleccionado: {importModal.file.name}
            </div>
          ) : null}

          {importModal?.result ? (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-2">
                <InfoCard label="Productos creados" value={String(importModal.result.successCount)} />
                <InfoCard label="Filas con observaciones" value={String(importModal.result.errorCount)} />
              </div>

              {importModal.result.errors.length > 0 ? (
                <div className="space-y-3">
                  {importModal.result.errors.map((error) => (
                    <div key={`${error.rowNumber}-${error.rowData}`} className="rounded-[22px] border border-rose-100 bg-rose-50/80 px-4 py-4">
                      <p className="text-sm font-semibold text-rose-700">Fila {error.rowNumber}</p>
                      <p className="mt-1 text-sm text-rose-700">{error.message}</p>
                      <p className="mt-2 text-xs text-rose-600">{error.rowData}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState
                  title="Carga completada sin observaciones"
                  description="Todos los productos del archivo fueron creados correctamente."
                />
              )}
            </div>
          ) : null}
        </div>
      </Modal>

      <Modal
        open={canImportStockReductions && stockReductionModal !== null}
        onClose={() => {
          if (!stockReductionMutation.isPending) {
            setStockReductionModal(null);
          }
        }}
        title="Reducir stock por CSV"
        description="Descuenta unidades usando codigo de barra y cantidad. Esta accion queda registrada en el historial de inventario."
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={downloadStockReductionTemplate}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-foreground"
            >
              Descargar plantilla
            </button>
            <button
              type="button"
              onClick={() => setStockReductionModal(null)}
              disabled={stockReductionMutation.isPending}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground disabled:opacity-50"
            >
              Cerrar
            </button>
            <button
              type="button"
              onClick={() => {
                if (stockReductionModal?.file) {
                  setFeedback(null);
                  stockReductionMutation.mutate(stockReductionModal.file);
                }
              }}
              disabled={stockReductionMutation.isPending || !stockReductionModal?.file}
              className="rounded-full bg-[linear-gradient(135deg,rgba(255,178,92,0.98),rgba(245,117,141,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {stockReductionMutation.isPending ? "Reduciendo..." : "Reducir stock"}
            </button>
          </div>
        }
      >
        <div className="space-y-5">
          <div className="rounded-[24px] border border-amber-100 bg-amber-50/80 p-4">
            <p className="text-sm font-semibold text-amber-900">Plantilla esperada</p>
            <p className="mt-1 text-sm text-amber-800">codigo_barra,cantidad</p>
            <p className="mt-2 text-xs text-amber-700">
              La cantidad siempre debe ser positiva. El sistema la restara del stock actual y rechazara filas que dejarian stock negativo.
            </p>
          </div>

          <label className="grid gap-2 text-sm">
            <span>Archivo CSV</span>
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(event) =>
                setStockReductionModal((current) =>
                  current
                    ? {
                        ...current,
                        file: event.target.files?.[0] ?? null,
                        result: null,
                      }
                    : current,
                )
              }
              className="rounded-2xl border border-input bg-background px-3 py-3"
            />
          </label>

          {stockReductionModal?.file ? (
            <div className="soft-chip w-fit">
              Archivo seleccionado: {stockReductionModal.file.name}
            </div>
          ) : null}

          {stockReductionModal?.result ? (
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-2">
                <InfoCard label="Productos ajustados" value={String(stockReductionModal.result.successCount)} />
                <InfoCard label="Filas con observaciones" value={String(stockReductionModal.result.errorCount)} />
              </div>

              {stockReductionModal.result.errors.length > 0 ? (
                <div className="space-y-3">
                  {stockReductionModal.result.errors.map((error) => (
                    <div key={`${error.rowNumber}-${error.rowData}`} className="rounded-[22px] border border-rose-100 bg-rose-50/80 px-4 py-4">
                      <p className="text-sm font-semibold text-rose-700">Fila {error.rowNumber}</p>
                      <p className="mt-1 text-sm text-rose-700">{error.message}</p>
                      <p className="mt-2 text-xs text-rose-600">{error.rowData}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState
                  title="Stock reducido sin observaciones"
                  description="Todas las filas fueron procesadas correctamente."
                />
              )}
            </div>
          ) : null}
        </div>
      </Modal>

      <Modal
        open={canCreateProducts && createOpen}
        onClose={() => setCreateOpen(false)}
        title={isStoreUser ? "Agregar producto" : "Crear producto"}
        description={
          isStoreUser
            ? "Carga tu producto, define el precio y deja listo el stock inicial para empezar a vender."
            : "El Espacio se resuelve automaticamente desde tu sesion. Solo define el producto y su Tienda responsable."
        }
        footer={
          <FooterActions
            submitLabel={createMutation.isPending ? "Creando..." : "Guardar producto"}
            formId="create-product-form"
            onCancel={() => setCreateOpen(false)}
            disabled={createMutation.isPending}
          />
        }
      >
        <ProductForm
          id="create-product-form"
          form={createForm}
          onChange={setCreateForm}
          collaborators={collaborators.map((collaborator) => ({ id: collaborator.id, fullName: collaborator.fullName }))}
          promotionGroups={createPromotionGroupsQuery.data ?? []}
          onDeletePromotionGroup={(group) => deletePromotionGroupMutation.mutate(group)}
          deletingPromotionGroupId={deletePromotionGroupMutation.variables?.id ?? null}
            onSubmit={(event) => {
              event.preventDefault();
              setFeedback(null);
              createMutation.mutate();
            }}
            stockLabel="Stock inicial"
            ownerLabel={isStoreUser ? "Esta Tienda" : "Tienda responsable"}
            ownerPlaceholder={isStoreUser ? "Producto asignado a tu Tienda" : "Selecciona una Tienda"}
            ownerLocked={isStoreUser}
          />
      </Modal>

      <Modal
        open={canEditProducts && editProduct !== null}
        onClose={() => setEditProduct(null)}
        title={editProduct ? `Editar ${editProduct.name}` : "Editar producto"}
        description={
          isStoreUser
            ? "Actualiza precio, stock y promocion de tus productos sin salir de esta vista."
            : "Actualiza precio, stock, promocion y revisa el historial completo del producto."
        }
        footer={
          <FooterActions
            submitLabel={updateMutation.isPending ? "Guardando..." : "Guardar cambios"}
            formId="edit-product-form"
            onCancel={() => setEditProduct(null)}
            disabled={updateMutation.isPending || editProduct === null}
          />
        }
      >
        {editProduct ? (
          <div className="space-y-6">
            <div className="grid gap-3 md:grid-cols-3">
              <InfoCard label="Codigo de barras" value={editProduct.barcode ?? "Sin codigo"} />
              <InfoCard label="Promocion activa" value={describePromotion(editProduct.promotion ?? null)} />
              <InfoCard label="Ultima actualizacion" value={editProduct.updatedAt ? formatDate(editProduct.updatedAt) : "Sin registro"} />
            </div>

            <ProductForm
              id="edit-product-form"
              form={editForm}
              onChange={setEditForm}
              collaborators={collaborators.map((collaborator) => ({ id: collaborator.id, fullName: collaborator.fullName }))}
              promotionGroups={editPromotionGroupsQuery.data ?? []}
              onDeletePromotionGroup={(group) => deletePromotionGroupMutation.mutate(group)}
              deletingPromotionGroupId={deletePromotionGroupMutation.variables?.id ?? null}
              onSubmit={(event) => {
                event.preventDefault();
                setFeedback(null);
                updateMutation.mutate();
              }}
              stockLabel="Stock actual"
              ownerLabel={isStoreUser ? "Esta Tienda" : "Tienda responsable"}
              ownerPlaceholder={isStoreUser ? "Producto asignado a tu Tienda" : "Selecciona una Tienda"}
              ownerLocked={isStoreUser}
            />

            <div className="rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,247,251,0.94))] p-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold">Historial del producto</h3>
                  <p className="text-sm text-muted-foreground">Revisa los cambios de precio, stock, descripcion y promocion de este producto.</p>
                </div>
                <span className="soft-chip">{auditQuery.data?.length ?? 0} eventos</span>
              </div>

              {auditQuery.isLoading ? <div className="mt-4"><FeedbackMessage kind="info" message="Cargando historial..." /></div> : null}
              {auditQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage kind="error" message={getErrorMessage(auditQuery.error, "No pudimos cargar el historial.")} />
                </div>
              ) : null}
              {auditQuery.data?.length === 0 ? (
                <div className="mt-4">
                  <EmptyState
                    title="Sin cambios registrados aun"
                    description="Cuando hagas ajustes de producto, el historial aparecera aqui."
                  />
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  {auditQuery.data?.map((entry) => (
                    <AuditRow key={entry.id} entry={entry} />
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        open={canViewHistory && historyProduct !== null}
        onClose={() => setHistoryProduct(null)}
        title={historyProduct ? `Historial de ${historyProduct.name}` : "Historial del producto"}
        description="Consulta los cambios registrados para este producto."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setHistoryProduct(null)}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground"
            >
              Cerrar
            </button>
          </div>
        }
      >
        {historyProduct ? (
          <div className="space-y-6">
            <div className="grid gap-3 md:grid-cols-3">
              <InfoCard label="Producto" value={historyProduct.name} />
              <InfoCard label="Codigo de barras" value={historyProduct.barcode ?? "Sin codigo"} />
              <InfoCard label="Ultima actualizacion" value={historyProduct.updatedAt ? formatDate(historyProduct.updatedAt) : "Sin registro"} />
            </div>

            <div className="rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,247,251,0.94))] p-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold">Historial del producto</h3>
                  <p className="text-sm text-muted-foreground">Revisa los cambios de precio, stock, descripcion y promocion de este producto.</p>
                </div>
                <span className="soft-chip">{auditQuery.data?.length ?? 0} eventos</span>
              </div>

              {auditQuery.isLoading ? <div className="mt-4"><FeedbackMessage kind="info" message="Cargando historial..." /></div> : null}
              {auditQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage kind="error" message={getErrorMessage(auditQuery.error, "No pudimos cargar el historial.")} />
                </div>
              ) : null}
              {auditQuery.data?.length === 0 ? (
                <div className="mt-4">
                  <EmptyState
                    title="Sin cambios registrados aun"
                    description="Todavia no hay movimientos de historial para este producto."
                  />
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  {auditQuery.data?.map((entry) => (
                    <AuditRow key={entry.id} entry={entry} />
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        open={canPrintBarcodes && barcodeModal !== null}
        onClose={() => setBarcodeModal(null)}
        title="Imprimir codigos de barras"
        description="Genera etiquetas pequenas de 4 x 2,5 cm listas para imprimir y pegar en tus productos."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setBarcodeModal(null)}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={() => barcodeMutation.mutate()}
              disabled={barcodeMutation.isPending || !(barcodeModal?.items.length)}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {barcodeMutation.isPending ? "Generando..." : "Descargar PDF"}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          {barcodeModal?.items.map((item) => (
            <div key={item.productId} className="flex flex-col gap-3 rounded-[22px] border border-white/80 bg-background/70 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-medium">{item.productName}</p>
                <p className="text-sm text-muted-foreground">Formato compacto de 4 x 2,5 cm en hoja A4.</p>
              </div>
              <label className="grid gap-2 text-sm sm:w-36">
                <span>Cantidad</span>
                <input
                  min="1"
                  type="number"
                  value={item.quantity}
                  onChange={(event) =>
                    setBarcodeModal((current) =>
                      current
                        ? {
                            items: current.items.map((candidate) =>
                              candidate.productId === item.productId ? { ...candidate, quantity: event.target.value } : candidate,
                            ),
                          }
                        : current,
                    )
                  }
                  className="rounded-2xl border border-input bg-background px-3 py-2"
                />
              </label>
            </div>
          ))}
        </div>
      </Modal>
    </section>
  );
}

function ProductForm({
  id,
  form,
  onChange,
  collaborators,
  onSubmit,
  stockLabel,
  ownerLabel,
  ownerPlaceholder,
  ownerLocked,
  promotionGroups,
  onDeletePromotionGroup,
  deletingPromotionGroupId,
}: {
  id: string;
  form: ProductFormState;
  onChange: (next: ProductFormState) => void;
  collaborators: Array<{ id: number; fullName: string }>;
  promotionGroups: ProductPromotionGroup[];
  onDeletePromotionGroup: (group: ProductPromotionGroup) => void;
  deletingPromotionGroupId: number | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  stockLabel: string;
  ownerLabel: string;
  ownerPlaceholder: string;
  ownerLocked: boolean;
}) {
  return (
    <form id={id} onSubmit={onSubmit} className="grid gap-5">
      <div className="grid gap-5 md:grid-cols-2">
        <label className="grid gap-2 text-sm">
          <span>Nombre del producto</span>
          <input required value={form.name} onChange={(event) => onChange({ ...form, name: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>{ownerLabel}</span>
          <select
            required
            value={form.ownerUserId}
            disabled={ownerLocked}
            onChange={(event) => onChange({ ...form, ownerUserId: event.target.value, promotionGroupName: "" })}
            className="rounded-2xl border border-input bg-background px-3 py-2.5 disabled:cursor-not-allowed disabled:bg-secondary/60"
          >
            <option value="">{ownerPlaceholder}</option>
            {collaborators.map((collaborator) => (
              <option key={collaborator.id} value={collaborator.id}>
                {collaborator.fullName}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-2 text-sm">
          <span>Precio de venta</span>
          <input required min="0.01" step="0.01" type="number" value={form.salePrice} onChange={(event) => onChange({ ...form, salePrice: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>{stockLabel}</span>
          <input required min="0" step="1" type="number" value={form.stock} onChange={(event) => onChange({ ...form, stock: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
      </div>

      <label className="grid gap-2 text-sm">
        <span>Descripcion</span>
        <textarea rows={4} value={form.description} onChange={(event) => onChange({ ...form, description: event.target.value })} className="rounded-[24px] border border-input bg-background px-3 py-3" />
      </label>

      <div className="rounded-[26px] border border-white/80 bg-white/80 p-5">
        <div className="flex flex-col gap-1">
          <h3 className="text-base font-semibold">Grupo promocional</h3>
          <p className="text-sm text-muted-foreground">
            Usa el mismo grupo en productos distintos para que una promocion por cantidad se combine entre ellos.
          </p>
        </div>
        <label className="mt-4 grid gap-2 text-sm">
          <span>Selecciona o escribe un grupo nuevo</span>
          <input
            list={`${id}-promotion-groups`}
            maxLength={120}
            value={form.promotionGroupName}
            onChange={(event) => onChange({ ...form, promotionGroupName: event.target.value })}
            placeholder="Ej: Photocards"
            className="rounded-2xl border border-input bg-background px-3 py-2.5"
          />
          <datalist id={`${id}-promotion-groups`}>
            {promotionGroups.map((group) => (
              <option key={group.id} value={group.name} />
            ))}
          </datalist>
          <span className="text-xs text-muted-foreground">
            Si escribes un nombre que no existe, se creara automaticamente al guardar el producto.
          </span>
        </label>
        {promotionGroups.length > 0 ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {promotionGroups.map((group) => (
              <span key={group.id} className="inline-flex items-center gap-2 rounded-full border border-white/90 bg-white px-3 py-1.5 text-xs font-semibold shadow-sm">
                {group.name}
                <button
                  type="button"
                  onClick={() => onDeletePromotionGroup(group)}
                  disabled={deletingPromotionGroupId === group.id}
                  className="text-muted-foreground transition hover:text-red-600 disabled:opacity-50"
                  title="Eliminar grupo promocional"
                >
                  Eliminar
                </button>
              </span>
            ))}
          </div>
        ) : null}
      </div>

      <div className="rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,252,255,0.98),rgba(255,246,250,0.94))] p-5">
        <div className="flex flex-col gap-1">
          <h3 className="text-base font-semibold">Promocion</h3>
          <p className="text-sm text-muted-foreground">Elige una promocion por cantidad o un descuento porcentual.</p>
        </div>

        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <label className="grid gap-2 text-sm">
            <span>Tipo de promocion</span>
            <select
              value={form.promotionType}
              onChange={(event) =>
                onChange({
                  ...form,
                  promotionType: event.target.value as ProductFormState["promotionType"],
                  promotionQuantity: "",
                  promotionPrice: "",
                  promotionPercentage: "",
                  promotionAppliesToCash: true,
                  promotionAppliesToDebit: true,
                })
              }
              className="rounded-2xl border border-input bg-background px-3 py-2.5"
            >
              <option value="NONE">Sin promocion</option>
              <option value="QUANTITY_BLOCK">Promocion por cantidad</option>
              <option value="PERCENTAGE_DISCOUNT">Descuento porcentual</option>
              <option value="PAYMENT_METHOD_DISCOUNT">Descuento por medio de pago</option>
            </select>
          </label>

          {form.promotionType === "QUANTITY_BLOCK" ? (
            <>
              <label className="grid gap-2 text-sm">
                <span>Cantidad</span>
                <input required min="2" step="1" type="number" value={form.promotionQuantity} onChange={(event) => onChange({ ...form, promotionQuantity: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
              </label>
              <label className="grid gap-2 text-sm">
                <span>Precio promocional</span>
                <input required min="0.01" step="0.01" type="number" value={form.promotionPrice} onChange={(event) => onChange({ ...form, promotionPrice: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
              </label>
            </>
          ) : null}

          {form.promotionType === "PERCENTAGE_DISCOUNT" ? (
            <label className="grid gap-2 text-sm">
              <span>Porcentaje</span>
              <input required min="0.01" max="100" step="0.01" type="number" value={form.promotionPercentage} onChange={(event) => onChange({ ...form, promotionPercentage: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
            </label>
          ) : null}

          {form.promotionType === "PAYMENT_METHOD_DISCOUNT" ? (
            <>
              <label className="grid gap-2 text-sm">
                <span>Porcentaje de descuento</span>
                <input required min="0.01" max="100" step="0.01" type="number" value={form.promotionPercentage} onChange={(event) => onChange({ ...form, promotionPercentage: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
              </label>
              <div className="grid gap-2 text-sm">
                <span>Medios de pago</span>
                <div className="flex flex-wrap gap-2">
                  <label className="inline-flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-4 py-2.5">
                    <input
                      type="checkbox"
                      checked={form.promotionAppliesToCash}
                      onChange={(event) => onChange({ ...form, promotionAppliesToCash: event.target.checked })}
                    />
                    <span>Efectivo</span>
                  </label>
                  <label className="inline-flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-4 py-2.5">
                    <input
                      type="checkbox"
                      checked={form.promotionAppliesToDebit}
                      onChange={(event) => onChange({ ...form, promotionAppliesToDebit: event.target.checked })}
                    />
                    <span>Debito</span>
                  </label>
                </div>
              </div>
            </>
          ) : null}

          {form.promotionType !== "NONE" ? (
            <label className="grid gap-2 text-sm">
              <span>Fecha de termino</span>
              <input
                type="date"
                value={form.promotionEndsAt}
                onChange={(event) => onChange({ ...form, promotionEndsAt: event.target.value })}
                className="rounded-2xl border border-input bg-background px-3 py-2.5"
              />
              <span className="text-xs text-muted-foreground">Dejalo vacio si la promocion sera indefinida.</span>
            </label>
          ) : null}
        </div>
      </div>
    </form>
  );
}

function FooterActions({ submitLabel, formId, onCancel, disabled }: { submitLabel: string; formId: string; onCancel: () => void; disabled: boolean }) {
  return (
    <div className="flex justify-end gap-3">
      <button type="button" onClick={onCancel} className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground">
        Cancelar
      </button>
      <button type="submit" form={formId} disabled={disabled} className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">
        {submitLabel}
      </button>
    </div>
  );
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="soft-subtle-surface p-4">
      <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-sm font-semibold">{value}</p>
    </div>
  );
}

function LowStockBar({ label, stock, maxStock }: { label: string; stock: number; maxStock: number }) {
  const percentage = maxStock > 0 ? Math.max((stock / maxStock) * 100, 8) : 8;

  return (
    <div className="grid gap-2 md:grid-cols-[170px_1fr_72px] md:items-center">
      <span className="text-sm font-medium text-foreground">{label}</span>
      <div className="h-3 overflow-hidden rounded-full bg-rose-100/80">
        <div
          className="h-full rounded-full bg-[linear-gradient(135deg,rgba(255,145,178,0.96),rgba(255,194,102,0.96))]"
          style={{ width: `${percentage}%` }}
        />
      </div>
      <span className="text-sm font-semibold text-rose-700 md:text-right">{stock} un.</span>
    </div>
  );
}

function FilterChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? "rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white shadow-[0_12px_24px_rgba(186,153,228,0.22)]"
          : "rounded-full border border-white/90 bg-white/85 px-4 py-2.5 text-sm font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
      }
    >
      {label}
    </button>
  );
}

function AuditRow({ entry }: { entry: ProductAuditLog }) {
  return (
    <div className="rounded-[22px] border border-white/80 bg-white/80 px-4 py-4 shadow-sm">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="font-medium">{entry.fieldName}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {entry.previousValue ?? "Sin valor previo"} {"->"} {entry.newValue ?? "Sin valor nuevo"}
          </p>
        </div>
        <div className="text-right text-xs text-muted-foreground">
          <p>{entry.createdBy}</p>
          <p>{formatDate(entry.createdAt)}</p>
        </div>
      </div>
    </div>
  );
}

function toFormState(product: Product): ProductFormState {
  return {
    name: product.name,
    ownerUserId: product.ownerUserId ? String(product.ownerUserId) : "",
    salePrice: String(resolveProductSalePrice(product)),
    stock: String(product.stock),
    description: product.description ?? "",
    promotionGroupName: product.promotionGroupName ?? "",
    promotionType: resolvePromotionType(product.promotion ?? null),
    promotionQuantity: product.promotion?.type === "QUANTITY_BLOCK" ? String(product.promotion.quantity ?? "") : "",
    promotionPrice: product.promotion?.type === "QUANTITY_BLOCK" ? String(product.promotion.promotionalPrice ?? "") : "",
    promotionPercentage:
      product.promotion?.type === "PERCENTAGE_DISCOUNT" || product.promotion?.type === "PAYMENT_METHOD_DISCOUNT"
        ? String(product.promotion.percentageDiscount ?? "")
        : "",
    promotionAppliesToCash: product.promotion?.type === "PAYMENT_METHOD_DISCOUNT" ? Boolean(product.promotion.appliesToCash) : true,
    promotionAppliesToDebit: product.promotion?.type === "PAYMENT_METHOD_DISCOUNT" ? Boolean(product.promotion.appliesToDebit) : true,
    promotionEndsAt: product.promotion?.endsAt ? toDateInputValue(product.promotion.endsAt) : "",
  };
}

function resolvePromotionType(promotion: ProductPromotion | null): ProductFormState["promotionType"] {
  if (!promotion) {
    return "NONE";
  }
  return promotion.type;
}

function buildPromotionInput(form: ProductFormState): ProductPromotionInput | undefined {
  if (form.promotionType === "NONE") {
    return undefined;
  }
  const endsAt = form.promotionEndsAt.trim() ? form.promotionEndsAt : undefined;
  if (form.promotionType === "QUANTITY_BLOCK") {
    return {
      type: "QUANTITY_BLOCK",
      quantity: Number(form.promotionQuantity),
      promotionalPrice: Number(form.promotionPrice),
      endsAt,
    };
  }
  if (form.promotionType === "PAYMENT_METHOD_DISCOUNT") {
    return {
      type: "PAYMENT_METHOD_DISCOUNT",
      percentageDiscount: Number(form.promotionPercentage),
      appliesToCash: form.promotionAppliesToCash,
      appliesToDebit: form.promotionAppliesToDebit,
      endsAt,
    };
  }
  return {
    type: "PERCENTAGE_DISCOUNT",
    percentageDiscount: Number(form.promotionPercentage),
    endsAt,
  };
}

function buildSku(name: string) {
  const base = name
    .trim()
    .toUpperCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
  return base || `PRODUCTO-${Date.now()}`;
}

function downloadTemplate() {
  const content = [
    "nombre,precio,stock,descripcion,colaborador_email,grupo_promocional,promocion_tipo,promocion_valor,promocion_fin",
    "Sticker BTS,2000,10,Pack brillante,camila@example.com,,,,",
    "Photocard Grupo A,1000,20,Photocard version A,lucia@example.com,Photocards,CANTIDAD,2x1500,",
    "Photocard Grupo B,1000,20,Photocard version B,lucia@example.com,Photocards,CANTIDAD,2x1500,",
    "Photocard especial,1500,12,Descuento general,lucia@example.com,Photocards,PORCENTAJE,15%,",
    "Album Kpop,12000,4,Descuento por pago,lucia@example.com,,MEDIO_PAGO,10%:efectivo|debito,2026-12-31",
  ].join("\n");

  const blob = new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8;" });
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "plantilla-productos-colabohub.csv";
  anchor.click();
  window.URL.revokeObjectURL(url);
}

function downloadStockReductionTemplate() {
  const content = [
    "codigo_barra,cantidad",
    "7501234567890,2",
    "7501234567891,1",
  ].join("\n");

  const blob = new Blob([`\uFEFF${content}`], { type: "text/csv;charset=utf-8;" });
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "plantilla-reduccion-stock-colabohub.csv";
  anchor.click();
  window.URL.revokeObjectURL(url);
}

function toggleSelection(productId: number, setSelected: Dispatch<SetStateAction<number[]>>) {
  setSelected((current) => (current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId]));
}

function truncate(value: string, maxLength: number) {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength - 1)}...`;
}

function describePromotion(promotion: ProductPromotion | null | undefined) {
  if (!promotion) {
    return "Sin promocion";
  }
  if (promotion.type === "QUANTITY_BLOCK") {
    return appendPromotionEndDate(`${promotion.quantity} x ${formatCurrency(promotion.promotionalPrice ?? 0)}`, promotion.endsAt);
  }
  if (promotion.type === "PAYMENT_METHOD_DISCOUNT") {
    const paymentMethods = [
      promotion.appliesToCash ? "efectivo" : null,
      promotion.appliesToDebit ? "debito" : null,
    ].filter(Boolean);
    return appendPromotionEndDate(
      `${promotion.percentageDiscount}% con ${paymentMethods.join(" o ") || "medio de pago seleccionado"}`,
      promotion.endsAt,
    );
  }
  return appendPromotionEndDate(`${promotion.percentageDiscount}% descuento`, promotion.endsAt);
}

function appendPromotionEndDate(label: string, endsAt: string | null | undefined) {
  return endsAt ? `${label} hasta ${formatDateOnly(endsAt)}` : `${label} indefinida`;
}

function resolveProductSalePrice(product: Product) {
  if (typeof product.salePrice === "number" && Number.isFinite(product.salePrice) && product.salePrice > 0) {
    return product.salePrice;
  }

  if (typeof product.price === "number" && Number.isFinite(product.price)) {
    return product.price;
  }

  return 0;
}

function formatCurrency(value: number) {
  const safeValue = Number.isFinite(value) ? value : 0;
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(safeValue);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-CL", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat("es-CL", { dateStyle: "medium" }).format(new Date(value));
}

function toDateInputValue(value: string) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santiago",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(value));
  const year = parts.find((part) => part.type === "year")?.value ?? "";
  const month = parts.find((part) => part.type === "month")?.value ?? "";
  const day = parts.find((part) => part.type === "day")?.value ?? "";
  return year && month && day ? `${year}-${month}-${day}` : value.slice(0, 10);
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}
