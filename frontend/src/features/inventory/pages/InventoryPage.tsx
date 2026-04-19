import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { adjustStock } from "@/features/inventory/api/adjustStock";
import { listStockMovements } from "@/features/inventory/api/listStockMovements";
import { listProducts } from "@/features/products/api/productApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { ApiError } from "@/shared/lib/api/client";
import { PageHeader } from "@/shared/components/ui/PageHeader";

export function InventoryPage() {
  const { roles, primaryRole } = useSession();
  const queryClient = useQueryClient();
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [quantityDelta, setQuantityDelta] = useState("0");
  const [reason, setReason] = useState("");
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const canAdjustStock = roles.some((role) => ["ADMIN_SYSTEM", "ADMIN_MARKET", "COLLABORATOR"].includes(role));
  const isCollaborator = primaryRole === "STORE_USER";

  const productsQuery = useQuery({
    queryKey: ["products", "inventory"],
    queryFn: () => listProducts({ size: 100 }),
  });

  const selectedProduct = useMemo(
    () => productsQuery.data?.content.find((product) => product.id === selectedProductId) ?? null,
    [productsQuery.data, selectedProductId],
  );
  const inventoryStats = useMemo(() => {
    const products = productsQuery.data?.content ?? [];
    return {
      visibleProducts: products.length,
      totalUnits: products.reduce((total, product) => total + product.stock, 0),
      lowStockProducts: products.filter((product) => product.stock <= 5).length,
      promotedProducts: products.filter((product) => product.hasPromotion).length,
    };
  }, [productsQuery.data]);

  const movementsQuery = useQuery({
    queryKey: ["stock-movements", selectedProductId],
    queryFn: () => listStockMovements(selectedProductId!),
    enabled: selectedProductId !== null,
  });

  const adjustStockMutation = useMutation({
    mutationFn: adjustStock,
    onSuccess: () => {
      setQuantityDelta("0");
      setReason("");
      setFeedback({ kind: "success", message: "Ajuste de stock aplicado correctamente." });
      queryClient.invalidateQueries({ queryKey: ["products"] });
      queryClient.invalidateQueries({ queryKey: ["stock-movements", selectedProductId] });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible aplicar el ajuste.") });
    },
  });

  const handleAdjustment = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedProductId) {
      return;
    }

    setFeedback(null);
    adjustStockMutation.mutate({
      productId: selectedProductId,
      quantityDelta: Number(quantityDelta),
      reason,
    });
  };

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mi stock" : "Stock"}
        description={
          isCollaborator
            ? "Vista clara de tu catalogo, stock disponible e historial reciente para controlar reposicion."
            : "Gestion visual del stock con historial legible y acciones operativas en una misma vista."
        }
        eyebrow={isCollaborator ? "Modo lectura" : "Operacion diaria"}
      />

      {isCollaborator ? (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <InventoryStatCard label="Productos visibles" value={String(inventoryStats.visibleProducts)} helper="Catalogo propio disponible hoy." />
            <InventoryStatCard label="Unidades en stock" value={String(inventoryStats.totalUnits)} helper="Suma total de unidades activas." />
            <InventoryStatCard label="Stock bajo" value={String(inventoryStats.lowStockProducts)} helper="Productos con 5 unidades o menos." />
            <InventoryStatCard label="Con promocion" value={String(inventoryStats.promotedProducts)} helper="Productos con alguna promo activa." />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
            <div className="soft-surface p-6">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-lg font-semibold">Catalogo propio</h2>
                  <p className="text-sm text-muted-foreground">Selecciona un producto para revisar stock, precio e historial.</p>
                </div>
                <span className="soft-chip">{inventoryStats.visibleProducts} visibles</span>
              </div>

              {productsQuery.isLoading ? <div className="mt-4"><FeedbackMessage kind="info" message="Cargando productos..." /></div> : null}
              {productsQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage kind="error" message={getErrorMessage(productsQuery.error, "No fue posible cargar los productos.")} />
                </div>
              ) : null}

              {productsQuery.data?.empty ? (
                <div className="mt-4">
                  <EmptyState
                    title="No hay productos disponibles"
                    description="Cuando tengas productos asignados a tu Tienda, apareceran aqui automaticamente."
                  />
                </div>
              ) : (
                <div className="mt-4 grid gap-3 md:grid-cols-2">
                  {productsQuery.data?.content.map((product) => (
                    <button
                      key={product.id}
                      type="button"
                      onClick={() => setSelectedProductId(product.id)}
                      className={[
                        "rounded-[24px] border px-4 py-4 text-left shadow-sm transition",
                        selectedProductId === product.id
                          ? "border-violet-200 bg-[linear-gradient(135deg,rgba(246,239,255,0.98),rgba(255,242,249,0.98))]"
                          : "border-white/85 bg-white/70 hover:-translate-y-0.5 hover:bg-white/85",
                      ].join(" ")}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold">{product.name}</p>
                          <p className="mt-1 text-sm text-muted-foreground">SKU: {product.sku}</p>
                        </div>
                        <span className={["soft-chip", product.stock <= 5 ? "text-rose-700" : "text-emerald-700"].join(" ")}>
                          Stock {product.stock}
                        </span>
                      </div>
                      <div className="mt-4 flex items-center justify-between gap-3 text-sm">
                        <span className="font-semibold text-foreground">{new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(product.salePrice)}</span>
                        <span className="text-muted-foreground">{product.hasPromotion ? "Promocion activa" : "Precio normal"}</span>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-6">
              <div className="soft-surface p-6">
                <h2 className="text-lg font-semibold">Producto seleccionado</h2>
                {selectedProduct ? (
                  <div className="mt-4 space-y-4">
                    <div className="rounded-[24px] border border-white/85 bg-white/75 p-4 shadow-sm">
                      <p className="text-xl font-semibold">{selectedProduct.name}</p>
                      <p className="mt-1 text-sm text-muted-foreground">{selectedProduct.description || "Sin descripcion breve."}</p>
                    </div>
                    <div className="grid gap-3 md:grid-cols-2">
                      <InfoBox label="SKU" value={selectedProduct.sku} />
                      <InfoBox label="Precio" value={new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(selectedProduct.salePrice)} />
                      <InfoBox label="Stock actual" value={String(selectedProduct.stock)} />
                      <InfoBox label="Promocion" value={selectedProduct.hasPromotion ? "Activa" : "No"} />
                    </div>
                  </div>
                ) : (
                  <div className="mt-4">
                    <EmptyState title="Selecciona un producto" description="Al elegir un producto podras revisar sus datos y el historial de movimientos." />
                  </div>
                )}
              </div>

              <div className="soft-surface p-6">
                <div className="mb-4 flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold">Historial reciente</h2>
                    <p className="text-sm text-muted-foreground">
                      {selectedProduct ? `Ultimos movimientos de ${selectedProduct.name}.` : "Selecciona un producto para revisar su historial."}
                    </p>
                  </div>
                  {selectedProduct ? <span className="soft-chip">Producto seleccionado</span> : null}
                </div>

                <div className="soft-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Fecha</th>
                        <th>Tipo</th>
                        <th>Cantidad</th>
                        <th>Antes</th>
                        <th>Despues</th>
                        <th>Referencia</th>
                      </tr>
                    </thead>
                    <tbody>
                      {movementsQuery.data?.map((movement) => (
                        <tr key={movement.id}>
                          <td>{formatMovementDate(movement.createdAt)}</td>
                          <td>{translateMovementType(movement.type)}</td>
                          <td>{movement.quantity}</td>
                          <td>{movement.previousStock}</td>
                          <td>{movement.newStock}</td>
                          <td>{translateReferenceType(movement.referenceType)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>

                  {movementsQuery.isLoading ? <p className="px-5 py-6 text-sm text-muted-foreground">Cargando movimientos...</p> : null}
                  {selectedProductId && movementsQuery.data?.length === 0 ? (
                    <div className="p-4">
                      <EmptyState
                        title="Sin movimientos registrados"
                        description="El producto seleccionado aun no tiene historial de inventario."
                      />
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          </div>
        </div>
      ) : (
      <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
        <div className="space-y-6">
          {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

          <div className="soft-surface p-6">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">Productos</h2>
              <span className="soft-chip">{productsQuery.data?.content.length ?? 0} visibles</span>
            </div>

            {productsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando productos..." /> : null}
            {productsQuery.isError ? (
              <FeedbackMessage kind="error" message={getErrorMessage(productsQuery.error, "No fue posible cargar los productos.")} />
            ) : null}
            {productsQuery.data?.empty ? (
              <div className="mt-4">
                <EmptyState
                  title="No hay productos disponibles"
                  description="Crea productos en el catalogo para poder gestionar inventario."
                />
              </div>
            ) : (
              <div className="mt-4 grid gap-3">
                {productsQuery.data?.content.map((product) => (
                  <button
                    key={product.id}
                    type="button"
                    onClick={() => setSelectedProductId(product.id)}
                    className={[
                      "rounded-[22px] border px-4 py-3 text-left text-sm shadow-sm transition",
                      selectedProductId === product.id
                        ? "border-violet-200 bg-[linear-gradient(135deg,rgba(246,239,255,0.98),rgba(255,242,249,0.98))]"
                        : "border-white/85 bg-white/70 hover:-translate-y-0.5 hover:bg-white/85",
                    ].join(" ")}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{product.name}</p>
                        <p className="text-muted-foreground">SKU: {product.sku}</p>
                      </div>
                      <span className={["soft-chip", product.stock <= 5 ? "text-rose-700" : "text-emerald-700"].join(" ")}>
                        Stock {product.stock}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {canAdjustStock ? (
            <form onSubmit={handleAdjustment} className="soft-surface p-6">
              <h2 className="text-lg font-semibold">Ajuste manual</h2>
              <p className="mt-1 text-sm text-muted-foreground">Solo disponible para perfiles operativos con permiso de escritura.</p>
              <div className="mt-4 grid gap-4">
                <label className="grid gap-2 text-sm">
                  <span>Producto seleccionado</span>
                  <input
                    readOnly
                    value={selectedProduct?.name ?? "Selecciona un producto"}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Cantidad (+/-)</span>
                  <input
                    required
                    step="1"
                    type="number"
                    value={quantityDelta}
                    onChange={(event) => setQuantityDelta(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Motivo</span>
                  <input
                    required
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>

                <button
                  type="submit"
                  disabled={!selectedProductId || adjustStockMutation.isPending || !canAdjustStock}
                  className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
                >
                  {adjustStockMutation.isPending ? "Aplicando..." : "Aplicar ajuste"}
                </button>
              </div>
            </form>
          ) : (
            <div className="soft-surface p-6">
              <h2 className="text-lg font-semibold">Modo lectura</h2>
              <p className="mt-3 text-sm leading-6 text-muted-foreground">
                Desde aqui puedes revisar tus productos, su stock y el historial reciente de movimientos.
              </p>
            </div>
          )}
        </div>

        <div className="soft-surface p-6">
          <div className="mb-4 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Historial de movimientos</h2>
              <p className="text-sm text-muted-foreground">
                {selectedProduct ? `Movimientos registrados para ${selectedProduct.name}.` : "Selecciona un producto."}
              </p>
            </div>
            {selectedProduct ? <span className="soft-chip">Producto seleccionado</span> : null}
          </div>

          <div className="soft-table">
            <table>
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Tipo</th>
                  <th>Cantidad</th>
                  <th>Antes</th>
                  <th>Despues</th>
                  <th>Referencia</th>
                </tr>
              </thead>
              <tbody>
                {movementsQuery.data?.map((movement) => (
                  <tr key={movement.id}>
                    <td>{formatMovementDate(movement.createdAt)}</td>
                    <td>{translateMovementType(movement.type)}</td>
                    <td>{movement.quantity}</td>
                    <td>{movement.previousStock}</td>
                    <td>{movement.newStock}</td>
                    <td>{translateReferenceType(movement.referenceType)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {movementsQuery.isLoading ? <p className="px-5 py-6 text-sm text-muted-foreground">Cargando movimientos...</p> : null}
            {selectedProductId && movementsQuery.data?.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  title="Sin movimientos registrados"
                  description="El producto seleccionado aun no tiene historial de inventario."
                />
              </div>
            ) : null}
          </div>
        </div>
      </div>
      )}
    </section>
  );
}

function InventoryStatCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return (
    <article className="soft-surface p-5">
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="mt-3 text-3xl font-semibold tracking-tight">{value}</p>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">{helper}</p>
    </article>
  );
}

function InfoBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[22px] border border-white/85 bg-white/75 p-4 shadow-sm">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-2 text-base font-semibold text-foreground">{value}</p>
    </div>
  );
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

function formatMovementDate(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function translateMovementType(value: string) {
  const labels: Record<string, string> = {
    SALE: "Venta",
    ADJUSTMENT: "Ajuste",
    INITIAL: "Inicial",
    INBOUND: "Entrada",
    OUTBOUND: "Salida",
  };

  return labels[value] ?? value;
}

function translateReferenceType(value: string) {
  const labels: Record<string, string> = {
    INITIAL_STOCK: "Stock inicial",
    SALE: "Venta",
    MANUAL_ADJUSTMENT: "Ajuste manual",
    PRODUCT_EDIT: "Edicion de producto",
  };

  return labels[value] ?? value;
}
