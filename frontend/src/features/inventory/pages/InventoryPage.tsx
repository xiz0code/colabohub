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
            ? "Vista de solo lectura de productos, stock visible e historial dentro de tu alcance permitido."
            : "Gestion visual del stock con historial legible y acciones operativas en una misma vista."
        }
        eyebrow={isCollaborator ? "Modo lectura" : "Operacion diaria"}
      />

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
                Como Colaborador solo puedes consultar productos, stock e historial dentro de tu alcance permitido.
              </p>
            </div>
          )}
        </div>

        <div className="soft-surface p-6">
          <div className="mb-4 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Historial de movimientos</h2>
              <p className="text-sm text-muted-foreground">
                {selectedProduct ? `Mostrando movimientos para ${selectedProduct.name}.` : "Selecciona un producto."}
              </p>
            </div>
            {selectedProduct ? <span className="soft-chip">Producto activo</span> : null}
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
                    <td>{new Date(movement.createdAt).toLocaleString()}</td>
                    <td>{movement.type}</td>
                    <td>{movement.quantity}</td>
                    <td>{movement.previousStock}</td>
                    <td>{movement.newStock}</td>
                    <td>{movement.referenceType}</td>
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
    </section>
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
