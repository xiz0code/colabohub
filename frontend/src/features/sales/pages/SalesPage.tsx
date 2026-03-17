import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { getSalesTodayDetails } from "@/features/reports/api/reportApi";
import {
  addPosSaleItem,
  cancelPosSale,
  confirmPosSale,
  createPosSale,
  getOpenPosSale,
  recalculatePosSale,
  removePosSaleItem,
  scanPosProduct,
  searchPosProducts,
  updatePosPaymentMethod,
  updatePosSaleItem,
  type PosProduct,
  type PosSale,
} from "@/features/sales/api/posApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type Feedback = {
  kind: "success" | "error" | "info";
  message: string;
};

export function SalesPage() {
  const queryClient = useQueryClient();
  const { user, primaryRole } = useSession();
  const [sale, setSale] = useState<PosSale | null>(null);
  const [search, setSearch] = useState("");
  const [searchResults, setSearchResults] = useState<PosProduct[]>([]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [isInitializing, setIsInitializing] = useState(false);
  const [isPosOpen, setIsPosOpen] = useState(true);
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const scannerInputRef = useRef<HTMLInputElement | null>(null);

  const activeMarketId = user?.marketIds[0] ?? null;
  const activeMarketName = user?.marketIds.length === 1 ? "tu Tienda asignada" : "la Tienda autenticada";
  const canOperatePos = primaryRole === "ADMIN_MARKET" && Boolean(activeMarketId);
  const isOpen = sale?.status === "OPEN";

  const salesTodayQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details"],
    queryFn: getSalesTodayDetails,
    enabled: canOperatePos,
  });

  const syncSale = (nextSale: PosSale, nextFeedback?: Feedback) => {
    setSale(nextSale);
    setSearch("");
    setSearchResults([]);
    if (nextFeedback) {
      setFeedback(nextFeedback);
    }
    window.requestAnimationFrame(() => scannerInputRef.current?.focus());
  };

  const initializeSale = async () => {
    if (!canOperatePos) {
      setSale(null);
      return;
    }

    setIsInitializing(true);
    setFeedback(null);
    setSearch("");
    setSearchResults([]);

    try {
      const currentOpenSale = await getOpenPosSale();
      if (currentOpenSale) {
        setSale(currentOpenSale);
        setFeedback({ kind: "info", message: "Ya existia una venta abierta para tu Tienda y se reutilizo." });
        return;
      }

      const createdSale = await createPosSale();
      setSale(createdSale);
      setFeedback({ kind: "success", message: "Caja POS lista para registrar una nueva venta." });
    } catch (error) {
      setSale(null);
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible preparar la venta POS.") });
    } finally {
      setIsInitializing(false);
    }
  };

  useEffect(() => {
    if (!canOperatePos) {
      return;
    }
    void initializeSale();
  }, [canOperatePos]);

  useEffect(() => {
    if (isPosOpen && isOpen) {
      scannerInputRef.current?.focus();
    }
  }, [isOpen, isPosOpen, sale?.id]);

  const addItemMutation = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      addPosSaleItem(sale!.id, productId, quantity),
    onSuccess: (nextSale) => syncSale(nextSale, { kind: "success", message: "Producto agregado a la venta." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible agregar el producto.") }),
  });

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      updatePosSaleItem(sale!.id, itemId, quantity),
    onSuccess: (nextSale) => syncSale(nextSale),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar la cantidad.") }),
  });

  const removeItemMutation = useMutation({
    mutationFn: (itemId: number) => removePosSaleItem(sale!.id, itemId),
    onSuccess: (nextSale) => syncSale(nextSale, { kind: "success", message: "Producto eliminado de la venta." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible quitar el producto.") }),
  });

  const paymentMethodMutation = useMutation({
    mutationFn: (paymentMethod: PosSale["paymentMethod"]) => updatePosPaymentMethod(sale!.id, paymentMethod),
    onSuccess: (nextSale) => syncSale(nextSale, { kind: "success", message: "Medio de pago actualizado." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar el medio de pago.") }),
  });

  const recalculateMutation = useMutation({
    mutationFn: () => recalculatePosSale(sale!.id),
    onSuccess: (nextSale) => syncSale(nextSale, { kind: "success", message: "Venta recalculada correctamente." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible recalcular la venta.") }),
  });

  const confirmMutation = useMutation({
    mutationFn: () => confirmPosSale(sale!.id),
    onSuccess: async (nextSale) => {
      syncSale(nextSale);
      setIsConfirmModalOpen(false);
      setIsPosOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["reports", "sales", "today", "details"] });
      setFeedback({ kind: "success", message: "Venta registrada correctamente" });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible confirmar la venta.") }),
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelPosSale(sale!.id),
    onSuccess: (nextSale) => syncSale(nextSale, { kind: "success", message: "Venta cancelada." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cancelar la venta.") }),
  });

  const reopenMutation = useMutation({
    mutationFn: async () => {
      const currentOpenSale = await getOpenPosSale();
      if (currentOpenSale) {
        return { sale: currentOpenSale, reused: true };
      }

      return {
        sale: await createPosSale(),
        reused: false,
      };
    },
    onSuccess: ({ sale: nextSale, reused }) => {
      syncSale(nextSale, {
        kind: reused ? "info" : "success",
        message: reused ? "Se recupero la venta abierta actual de tu Tienda." : "Nueva venta abierta para tu Tienda.",
      });
      setIsPosOpen(true);
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible abrir la caja POS.") }),
  });

  const isBusy = useMemo(
    () =>
      isInitializing ||
      addItemMutation.isPending ||
      updateItemMutation.isPending ||
      removeItemMutation.isPending ||
      paymentMethodMutation.isPending ||
      recalculateMutation.isPending ||
      confirmMutation.isPending ||
      cancelMutation.isPending ||
      reopenMutation.isPending,
    [
      addItemMutation.isPending,
      cancelMutation.isPending,
      confirmMutation.isPending,
      isInitializing,
      paymentMethodMutation.isPending,
      recalculateMutation.isPending,
      removeItemMutation.isPending,
      reopenMutation.isPending,
      updateItemMutation.isPending,
    ],
  );

  const commissionUfAmount = useMemo(
    () => sale?.storeSummaries.reduce((total, summary) => total + summary.commission1Amount, 0) ?? 0,
    [sale?.storeSummaries],
  );
  const commissionPercentageAmount = useMemo(
    () => sale?.storeSummaries.reduce((total, summary) => total + summary.commission2Amount, 0) ?? 0,
    [sale?.storeSummaries],
  );

  const handleResolveAndAdd = async () => {
    const normalizedQuery = search.trim();
    if (!sale || !isOpen || normalizedQuery.length === 0) {
      return;
    }

    setFeedback(null);
    setSearchResults([]);

    try {
      const scannedProduct = await scanPosProduct(normalizedQuery);
      addItemMutation.mutate({ productId: scannedProduct.id, quantity: 1 });
      return;
    } catch (error) {
      const isNotFound = error instanceof ApiError && error.status === 404;
      if (!isNotFound) {
        setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible resolver el producto.") });
        return;
      }
    }

    try {
      const matches = await searchPosProducts(normalizedQuery);
      if (matches.length === 1) {
        addItemMutation.mutate({ productId: matches[0].id, quantity: 1 });
        return;
      }

      if (matches.length === 0) {
        setFeedback({ kind: "error", message: "No se encontraron productos activos con esa busqueda." });
        return;
      }

      setSearchResults(matches);
      setFeedback({ kind: "info", message: "Selecciona uno de los productos encontrados para agregarlo." });
    } catch (error) {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible buscar productos POS.") });
    }
  };

  if (!canOperatePos) {
    return (
      <section>
        <PageHeader
          title="Ventas"
          description="El POS operativo esta disponible solo para Administradores de Tienda con una Tienda autenticada."
        />
        <div className="soft-surface p-8">
          <EmptyState
            title="No tienes acceso operativo al POS"
            description="Esta seccion se habilita cuando existe una Tienda autenticada para operar ventas."
          />
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Ventas"
        description="Opera tu caja con una sola venta abierta por Tienda, total claro para cobrar y seguimiento diario inmediato."
        eyebrow="Caja POS"
      />

      {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

      <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
        <div className="soft-surface p-6">
          <div className="space-y-4">
            <div>
              <p className="text-sm text-muted-foreground">Caja autenticada</p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight">POS de {activeMarketName}</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                La Tienda se resuelve automaticamente desde tu sesion. No necesitas seleccionarla manualmente.
              </p>
            </div>

            <div className="soft-subtle-surface grid gap-3 p-4 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Venta activa</span>
                <span className="font-medium">{sale?.saleNumber ?? "Sin venta abierta"}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Estado</span>
                <span className="font-medium">{sale?.status ?? "Pendiente"}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Medio de pago</span>
                <span className="font-medium">{sale?.paymentMethod ?? "Sin definir"}</span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => {
                if (sale) {
                  setIsPosOpen(true);
                  return;
                }
                reopenMutation.mutate();
              }}
              disabled={isBusy}
              className="w-full rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {reopenMutation.isPending ? "Abriendo caja..." : sale ? "Abrir caja POS" : "Abrir o recuperar venta"}
            </button>
          </div>
        </div>

        <div className="soft-surface p-6">
          <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Ventas registradas hoy</h2>
              <p className="text-sm text-muted-foreground">Se actualizan automaticamente cuando confirmas una venta.</p>
            </div>
            {salesTodayQuery.data ? <span className="soft-chip">{salesTodayQuery.data.salesCount} venta(s)</span> : null}
          </div>

          {salesTodayQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando ventas del dia..." /> : null}
          {salesTodayQuery.isError ? (
            <FeedbackMessage
              kind="error"
              message={getErrorMessage(salesTodayQuery.error, "No fue posible cargar las ventas del dia.")}
            />
          ) : null}

          {salesTodayQuery.data?.sales.length ? (
            <div className="soft-table mt-4">
              <table>
                <thead>
                  <tr>
                    <th>Venta</th>
                    <th>Hora</th>
                    <th>Total</th>
                    <th>Comision</th>
                    <th>Neto</th>
                  </tr>
                </thead>
                <tbody>
                  {salesTodayQuery.data.sales.map((dailySale) => (
                    <tr key={dailySale.saleId}>
                      <td className="font-medium">{dailySale.saleNumber}</td>
                      <td>{formatDateTime(dailySale.confirmedAt)}</td>
                      <td>{formatMoney(dailySale.totalAmount)}</td>
                      <td>{formatMoney(dailySale.totalCommissionAmount)}</td>
                      <td>{formatMoney(dailySale.totalNetAmount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            !salesTodayQuery.isLoading && (
              <div className="mt-4">
                <EmptyState
                  title="Aun no hay ventas confirmadas"
                  description="Cuando registres una venta desde la caja, aparecera aqui automaticamente."
                />
              </div>
            )
          )}
        </div>
      </div>

      <Modal
        open={isPosOpen}
        title="Caja POS"
        description="Registra productos, revisa el total a cobrar y confirma la venta solo cuando estes listo."
        onClose={() => {
          setIsPosOpen(false);
          setIsConfirmModalOpen(false);
        }}
      >
        {isInitializing ? (
          <FeedbackMessage kind="info" message="Preparando la caja POS..." />
        ) : !sale ? (
          <EmptyState
            title="No hay una venta disponible"
            description="Abre o recupera una venta para comenzar a cobrar."
          />
        ) : (
          <div className="grid gap-6 xl:grid-cols-[330px_1fr]">
            <div className="space-y-5">
              <div className="rounded-[30px] border border-violet-100 bg-[linear-gradient(180deg,rgba(255,248,252,0.98),rgba(246,243,255,0.96))] p-5 shadow-[0_18px_45px_rgba(186,168,223,0.15)]">
                <p className="text-xs font-semibold uppercase tracking-[0.3em] text-violet-500">Total a cobrar</p>
                <p className="mt-4 text-5xl font-black tracking-tight text-slate-900">{formatMoney(sale.totalAmount)}</p>
                <div className="mt-5 grid gap-2 text-sm">
                  <BreakdownRow label="Subtotal" value={formatMoney(sale.subtotalAmount)} />
                  <BreakdownRow
                    label={`Comision UF${sale.commissionUfValue !== null ? ` (${formatUfRate(sale.commissionUfValue)})` : ""}`}
                    value={formatMoney(commissionUfAmount)}
                  />
                  <BreakdownRow
                    label={`Comision %${sale.commissionPercentageValue !== null ? ` (${formatPercentageRate(sale.commissionPercentageValue)})` : ""}`}
                    value={formatMoney(commissionPercentageAmount)}
                  />
                </div>
              </div>

              {!isOpen ? (
                <FeedbackMessage kind="info" message="Esta venta es inmutable. Abre o recupera una nueva para seguir operando." />
              ) : null}

              <div className="soft-subtle-surface grid gap-4 p-4">
                <label className="grid gap-2 text-sm">
                  <span>Buscar por barcode, SKU o nombre</span>
                  <div className="flex gap-2">
                    <input
                      ref={scannerInputRef}
                      autoFocus
                      value={search}
                      disabled={!isOpen}
                      onChange={(event) => setSearch(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          void handleResolveAndAdd();
                        }
                      }}
                      className="flex-1 rounded-2xl border border-input bg-background px-3 py-2"
                      placeholder="Escanear o buscar producto"
                    />
                    <button
                      type="button"
                      disabled={!isOpen || isBusy || search.trim().length === 0}
                      onClick={() => void handleResolveAndAdd()}
                      className="rounded-2xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                    >
                      {addItemMutation.isPending ? "Agregando..." : "Agregar"}
                    </button>
                  </div>
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Medio de pago</span>
                  <select
                    value={sale.paymentMethod}
                    disabled={!isOpen || isBusy}
                    onChange={(event) => paymentMethodMutation.mutate(event.target.value as PosSale["paymentMethod"])}
                    className="rounded-2xl border border-input bg-background px-3 py-2"
                  >
                    <option value="CASH">Efectivo</option>
                    <option value="CREDIT">Credito</option>
                    <option value="DEBITO">Debito</option>
                    <option value="TRANSFER">Transferencia</option>
                  </select>
                </label>

                {sale.paymentMethod === "DEBITO" && sale.ufValue !== null ? (
                  <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                    UF aplicada en esta venta: {formatUfValue(sale.ufValue)}
                  </div>
                ) : null}

                <div className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    disabled={!isOpen || isBusy}
                    onClick={() => recalculateMutation.mutate()}
                    className="rounded-2xl border border-border px-4 py-3 text-sm font-semibold disabled:opacity-50"
                  >
                    {recalculateMutation.isPending ? "Recalculando..." : "Recalcular"}
                  </button>
                  <button
                    type="button"
                    disabled={!isOpen || isBusy || sale.items.length === 0}
                    onClick={() => setIsConfirmModalOpen(true)}
                    className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    Confirmar venta
                  </button>
                  <button
                    type="button"
                    disabled={!isOpen || isBusy}
                    onClick={() => cancelMutation.mutate()}
                    className="rounded-2xl border border-red-200 px-4 py-3 text-sm font-semibold text-red-700 disabled:opacity-50"
                  >
                    {cancelMutation.isPending ? "Cancelando..." : "Cancelar venta"}
                  </button>
                </div>
              </div>

              {searchResults.length > 0 ? (
                <div className="soft-subtle-surface p-4">
                  <p className="mb-3 text-sm font-medium">Resultados encontrados</p>
                  <div className="grid gap-2">
                    {searchResults.map((product) => (
                      <button
                        key={product.id}
                        type="button"
                        disabled={!isOpen || isBusy}
                        onClick={() => addItemMutation.mutate({ productId: product.id, quantity: 1 })}
                        className="rounded-2xl border border-border/70 px-3 py-3 text-left text-sm transition hover:bg-secondary/40 disabled:opacity-50"
                      >
                        <p className="font-medium">{product.name}</p>
                        <p className="text-muted-foreground">
                          {product.storeName} | SKU: {product.sku} | Barcode: {product.barcode} | Stock: {product.stock}
                        </p>
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>

            <div className="space-y-5">
              <div className="soft-subtle-surface p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-semibold">{sale.saleNumber}</h2>
                    <p className="text-sm text-muted-foreground">Estado actual: {sale.status}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => reopenMutation.mutate()}
                    disabled={isBusy}
                    className="rounded-full border border-border px-4 py-2 text-sm font-semibold disabled:opacity-50"
                  >
                    {reopenMutation.isPending ? "Abriendo..." : "Nueva venta"}
                  </button>
                </div>
              </div>

              <div className="soft-subtle-surface p-4">
                <h2 className="text-lg font-semibold">Lineas de la venta</h2>
                {sale.items.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="La venta aun no tiene productos"
                      description="Escanea o busca un producto para empezar a cobrar."
                    />
                  </div>
                ) : (
                  <div className="soft-table mt-4">
                    <table>
                      <thead>
                        <tr>
                          <th>Producto</th>
                          <th>Colaborador</th>
                          <th>Cant.</th>
                          <th>Precio</th>
                          <th>Total</th>
                          <th>Acciones</th>
                        </tr>
                      </thead>
                      <tbody>
                        {sale.items.map((item) => (
                          <tr key={item.id}>
                            <td>
                              <p className="font-medium">{item.productName}</p>
                              <p className="text-muted-foreground">
                                {item.storeName} | SKU: {item.sku} | Barcode: {item.barcode}
                              </p>
                            </td>
                            <td>{item.collaboratorName ?? "Sin colaborador"}</td>
                            <td>
                              <input
                                min={1}
                                type="number"
                                defaultValue={item.quantity}
                                disabled={!isOpen || isBusy}
                                onBlur={(event) => {
                                  const nextQuantity = Number(event.target.value);
                                  if (Number.isFinite(nextQuantity) && nextQuantity >= 1 && nextQuantity !== item.quantity) {
                                    updateItemMutation.mutate({ itemId: item.id, quantity: nextQuantity });
                                  }
                                }}
                                className="w-20 rounded-xl border border-input bg-background px-3 py-2"
                              />
                            </td>
                            <td>{formatMoney(item.baseUnitPrice)}</td>
                            <td>
                              <p className="font-medium">{formatMoney(item.subtotal)}</p>
                              {sale.paymentMethod === "DEBITO" ? (
                                <p className="text-muted-foreground">Neto: {formatMoney(item.netAmount)}</p>
                              ) : null}
                            </td>
                            <td>
                              <button
                                type="button"
                                disabled={!isOpen || isBusy}
                                onClick={() => removeItemMutation.mutate(item.id)}
                                className="rounded-full border border-border px-3 py-1 text-xs disabled:opacity-50"
                              >
                                Quitar
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="soft-subtle-surface p-4">
                <h2 className="text-lg font-semibold">Resumen por colaborador / Tienda</h2>
                {sale.storeSummaries.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="Sin resumen todavia"
                      description="El resumen se completara cuando agregues productos a la venta."
                    />
                  </div>
                ) : (
                  <div className="mt-4 grid gap-3">
                    {sale.storeSummaries.map((summary) => (
                      <article key={summary.storeId} className="rounded-2xl border border-border/70 bg-background/80 p-4 text-sm">
                        <div className="flex items-center justify-between gap-3">
                          <p className="font-semibold">{summary.storeName}</p>
                          <p className="text-muted-foreground">
                            {summary.lineCount} linea(s) | {summary.unitCount} unidad(es)
                          </p>
                        </div>
                        <div className="mt-3 grid gap-1">
                          <span>Subtotal: {formatMoney(summary.subtotalAmount)}</span>
                          <span>Comision UF: {formatMoney(summary.commission1Amount)}</span>
                          <span>Comision %: {formatMoney(summary.commission2Amount)}</span>
                          <span>IVA comision: {formatMoney(summary.commissionIvaAmount)}</span>
                          <span className="font-medium">Neto tienda: {formatMoney(summary.netAmount)}</span>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={isConfirmModalOpen}
        title="Confirmar venta"
        description="La venta quedara registrada y luego no podra modificarse."
        onClose={() => setIsConfirmModalOpen(false)}
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={() => setIsConfirmModalOpen(false)}
              className="rounded-2xl border border-border px-4 py-2 text-sm font-semibold"
            >
              Volver
            </button>
            <button
              type="button"
              onClick={() => confirmMutation.mutate()}
              disabled={confirmMutation.isPending}
              className="rounded-2xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {confirmMutation.isPending ? "Confirmando..." : "Confirmar venta"}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <div className="rounded-[28px] border border-violet-100 bg-[linear-gradient(180deg,rgba(255,248,252,0.98),rgba(246,243,255,0.96))] p-5">
            <p className="text-sm font-semibold uppercase tracking-[0.22em] text-violet-500">Total a cobrar</p>
            <p className="mt-3 text-4xl font-black tracking-tight text-slate-900">
              {sale ? formatMoney(sale.totalAmount) : formatMoney(0)}
            </p>
          </div>
          <p className="text-sm text-muted-foreground">Deseas confirmar esta venta?</p>
        </div>
      </Modal>
    </section>
  );
}

function BreakdownRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-semibold text-slate-900">{value}</span>
    </div>
  );
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatUfValue(value: number) {
  return new Intl.NumberFormat("es-CL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatUfRate(value: number) {
  return `${value.toFixed(5).replace(".", ",")} UF`;
}

function formatPercentageRate(value: number) {
  return `${(value * 100).toFixed(2).replace(".", ",")}%`;
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
