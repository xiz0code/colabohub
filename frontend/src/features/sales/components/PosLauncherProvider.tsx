import {
  createContext,
  type PropsWithChildren,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  addPosSaleItem,
  cancelPosSale,
  confirmPosSale,
  createPosSale,
  recalculatePosSale,
  removePosSaleItem,
  scanPosProduct,
  searchPosProducts,
  updatePosPaymentMethod,
  updatePosSaleItem,
  type PosProduct,
  type PosSale,
  type PosSaleSummary,
} from "@/features/sales/api/posApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { ApiError } from "@/shared/lib/api/client";

type Feedback = {
  kind: "success" | "error" | "info";
  message: string;
};

type PosLauncherContextValue = {
  openPos: () => void;
  openPosWithSale: (sale: PosSale) => void;
  canOperatePos: boolean;
  isOpening: boolean;
};

const PosLauncherContext = createContext<PosLauncherContextValue | undefined>(undefined);

export function PosLauncherProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const { user, primaryRole } = useSession();
  const [activeSale, setActiveSale] = useState<PosSale | null>(null);
  const [search, setSearch] = useState("");
  const [searchResults, setSearchResults] = useState<PosProduct[]>([]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [isPosModalOpen, setIsPosModalOpen] = useState(false);
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [isPosCloseConfirmOpen, setIsPosCloseConfirmOpen] = useState(false);
  const [amountReceived, setAmountReceived] = useState("");
  const [isSearchingProducts, setIsSearchingProducts] = useState(false);
  const scannerInputRef = useRef<HTMLInputElement | null>(null);

  const activeMarketName = user?.activeMarketName ?? "tu Espacio";
  const canOperatePos = (primaryRole === "ADMIN_MARKET" || primaryRole === "SELLER") && Boolean(user?.activeMarketId);

  const syncSale = (nextSale: PosSale, nextFeedback?: Feedback) => {
    setActiveSale(nextSale);
    setSearch("");
    setSearchResults([]);
    if (nextSale.paymentMethod !== "CASH") {
      setAmountReceived("");
    }
    if (nextFeedback) {
      setFeedback(nextFeedback);
    }
    queryClient.setQueryData(["pos", "sales", nextSale.id], nextSale);
    void queryClient.invalidateQueries({ queryKey: ["pos", "sales"] });
    window.requestAnimationFrame(() => scannerInputRef.current?.focus());
  };

  const openSaleMutation = useMutation({
    mutationFn: () => createPosSale(),
    onSuccess: (sale) => {
      syncSale(sale, {
        kind: "info",
        message: "Caja lista. Si ya existia una venta abierta, se reutilizo automaticamente.",
      });
      setIsPosModalOpen(true);
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible abrir la caja POS.") }),
  });

  const addItemMutation = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      addPosSaleItem(activeSale!.id, productId, quantity),
    onSuccess: (sale) => syncSale(sale, { kind: "success", message: "Producto agregado a la venta." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible agregar el producto.") }),
  });

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      updatePosSaleItem(activeSale!.id, itemId, quantity),
    onSuccess: (sale) => syncSale(sale),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar la cantidad.") }),
  });

  const removeItemMutation = useMutation({
    mutationFn: (itemId: number) => removePosSaleItem(activeSale!.id, itemId),
    onSuccess: (sale) => syncSale(sale, { kind: "success", message: "Producto quitado de la venta." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible quitar el producto.") }),
  });

  const paymentMethodMutation = useMutation({
    mutationFn: (paymentMethod: PosSale["paymentMethod"]) => updatePosPaymentMethod(activeSale!.id, paymentMethod),
    onSuccess: (sale) => syncSale(sale, { kind: "success", message: "Medio de pago actualizado." }),
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar el medio de pago.") }),
  });

  const recalculateMutation = useMutation({
    mutationFn: () => recalculatePosSale(activeSale!.id),
    onSuccess: (sale) => syncSale(sale, { kind: "success", message: "Venta recalculada correctamente." }),
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible recalcular la venta.") }),
  });

  const confirmMutation = useMutation({
    mutationFn: () => confirmPosSale(activeSale!.id),
    onSuccess: (sale) => {
      syncSale(sale, { kind: "success", message: "Venta registrada correctamente" });
      setIsConfirmModalOpen(false);
      setIsPosModalOpen(false);
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible confirmar la venta.") }),
  });

  const cancelActiveSaleMutation = useMutation({
    mutationFn: () => cancelPosSale(activeSale!.id, "Venta cancelada desde la caja POS."),
    onSuccess: (sale) => {
      queryClient.setQueryData(["pos", "sales", sale.id], sale);
      void queryClient.invalidateQueries({ queryKey: ["pos", "sales"] });
      setActiveSale(sale);
      setIsPosCloseConfirmOpen(false);
      setIsPosModalOpen(false);
      setFeedback({ kind: "success", message: "Venta cancelada correctamente desde la caja." });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cancelar la venta desde la caja.") });
    },
  });

  const isPosBusy =
    openSaleMutation.isPending ||
    addItemMutation.isPending ||
    updateItemMutation.isPending ||
    removeItemMutation.isPending ||
    paymentMethodMutation.isPending ||
    recalculateMutation.isPending ||
    confirmMutation.isPending ||
    cancelActiveSaleMutation.isPending;

  const isOpen = activeSale?.status === "OPEN";
  const amountReceivedValue = Number(amountReceived);
  const isCashPayment = activeSale?.paymentMethod === "CASH";
  const changeAmount =
    isCashPayment && Number.isFinite(amountReceivedValue)
      ? Math.max(amountReceivedValue - (activeSale?.totalAmount ?? 0), 0)
      : 0;
  const isCashAmountInsufficient =
    isCashPayment &&
    amountReceived.trim().length > 0 &&
    Number.isFinite(amountReceivedValue) &&
    amountReceivedValue < (activeSale?.totalAmount ?? 0);
  const getCartQuantityForProduct = (productId: number) =>
    activeSale?.items
      .filter((item) => item.productId === productId)
      .reduce((total, item) => total + item.quantity, 0) ?? 0;

  const canAddProductToCart = (product: PosProduct) => product.stock > 0 && getCartQuantityForProduct(product.id) < product.stock;

  const handleAddProduct = (product: PosProduct) => {
    if (!activeSale || !isOpen || isPosBusy) {
      return;
    }

    if (product.stock <= 0) {
      setFeedback({ kind: "error", message: `${product.name} no tiene stock disponible.` });
      return;
    }

    const requestedQuantity = getCartQuantityForProduct(product.id) + 1;
    if (requestedQuantity > product.stock) {
      setFeedback({
        kind: "error",
        message: `No puedes vender ${requestedQuantity} unidad(es) de ${product.name}. Stock disponible: ${product.stock}.`,
      });
      return;
    }

    addItemMutation.mutate({ productId: product.id, quantity: 1 });
  };

  useEffect(() => {
    if (!isPosModalOpen || !isOpen) {
      return;
    }

    const normalizedQuery = search.trim();
    if (normalizedQuery.length < 2) {
      setSearchResults([]);
      setIsSearchingProducts(false);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        setIsSearchingProducts(true);
        const matches = await searchPosProducts(normalizedQuery);
        setSearchResults(matches);
      } catch {
        setSearchResults([]);
      } finally {
        setIsSearchingProducts(false);
      }
    }, 220);

    return () => window.clearTimeout(timeoutId);
  }, [isOpen, isPosModalOpen, search]);

  const handleResolveAndAdd = async () => {
    const normalizedQuery = search.trim();
    if (!activeSale || !isOpen || normalizedQuery.length === 0) {
      return;
    }

    setSearchResults([]);
    setFeedback(null);

    try {
      const scannedProduct = await scanPosProduct(normalizedQuery);
      handleAddProduct(scannedProduct);
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
        handleAddProduct(matches[0]);
        return;
      }

      if (matches.length === 0) {
        setFeedback({ kind: "error", message: "No encontramos productos activos con esa busqueda." });
        return;
      }

      setSearchResults(matches);
      setFeedback({ kind: "info", message: "Selecciona un producto para agregarlo a la venta." });
    } catch (error) {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible buscar productos.") });
    }
  };

  const handlePosCloseRequest = () => {
    if (!activeSale) {
      setIsPosModalOpen(false);
      return;
    }

    if (activeSale.status !== "OPEN" || activeSale.items.length === 0) {
      setIsPosModalOpen(false);
      return;
    }

    setIsPosCloseConfirmOpen(true);
  };

  const openPos = () => {
    if (!canOperatePos || openSaleMutation.isPending) {
      return;
    }
    openSaleMutation.mutate();
  };

  const openPosWithSale = (sale: PosSale) => {
    syncSale(sale);
    setIsPosModalOpen(true);
  };

  return (
    <PosLauncherContext.Provider
      value={{
        openPos,
        openPosWithSale,
        canOperatePos,
        isOpening: openSaleMutation.isPending,
      }}
    >
      {children}

      {feedback ? (
        <div className="fixed right-6 top-24 z-50 max-w-md">
          <FeedbackMessage kind={feedback.kind} message={feedback.message} />
        </div>
      ) : null}

      <Modal
        open={isPosModalOpen}
        onClose={handlePosCloseRequest}
        title="Caja POS"
        description="La venta se prepara automaticamente para que puedas cobrar sin pasos extra."
        maxWidthClassName="max-w-6xl"
      >
        {!activeSale ? (
          <FeedbackMessage kind="info" message="Preparando la caja POS..." />
        ) : (
          <div className="grid gap-6 xl:grid-cols-[1.15fr_0.95fr]">
            <div className="space-y-5">
              <div className="soft-subtle-surface overflow-hidden p-0">
                <div className="border-b border-border/60 bg-[linear-gradient(135deg,rgba(255,248,252,0.92),rgba(244,241,255,0.98))] px-5 py-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-[0.28em] text-violet-500">Caja activa</p>
                      <h2 className="mt-2 text-xl font-semibold text-foreground">{activeSale.saleNumber}</h2>
                      <p className="mt-1 text-sm text-muted-foreground">Lista para cobrar en {activeMarketName}</p>
                    </div>
                    <StatusBadge status={activeSale.status} />
                  </div>
                </div>

                <div className="space-y-4 p-5">
                  {!isOpen ? (
                    <FeedbackMessage
                      kind="info"
                      message="Esta venta ya no se puede editar. Abre una nueva para seguir cobrando."
                    />
                  ) : null}

                  <label className="grid gap-2 text-sm">
                    <span className="font-medium text-foreground">Escanear o buscar producto</span>
                    <div className="flex gap-2">
                      <input
                        ref={scannerInputRef}
                        value={search}
                        autoFocus
                        disabled={!isOpen}
                        onChange={(event) => setSearch(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            event.preventDefault();
                            if (searchResults.length > 0) {
                              handleAddProduct(searchResults[0]);
                              return;
                            }
                            void handleResolveAndAdd();
                          }
                        }}
                        className="flex-1 rounded-2xl border border-input bg-background px-4 py-3"
                        placeholder="Busca por nombre, SKU o codigo de barras"
                      />
                      <button
                        type="button"
                        onClick={() => void handleResolveAndAdd()}
                        disabled={!isOpen || isPosBusy || search.trim().length === 0}
                        className="rounded-2xl bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                      >
                        {addItemMutation.isPending ? "Agregando..." : "Agregar"}
                      </button>
                    </div>
                  </label>

                  <div className="rounded-[24px] border border-dashed border-violet-200/80 bg-violet-50/40 px-4 py-3 text-sm text-muted-foreground">
                    El lector esta listo para trabajar con codigo de barras, SKU o nombre. Presiona Enter para agregar el
                    primer resultado.
                  </div>
                </div>
              </div>

              <div className="soft-subtle-surface p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Productos para agregar</h3>
                    <p className="text-sm text-muted-foreground">
                      Selecciona un resultado o escanea directamente desde la caja.
                    </p>
                  </div>
                  <span className="rounded-full bg-secondary/70 px-3 py-1 text-xs font-semibold text-muted-foreground">
                    {isSearchingProducts ? "Buscando..." : `${searchResults.length} resultado${searchResults.length === 1 ? "" : "s"}`}
                  </span>
                </div>

                {searchResults.length > 0 ? (
                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    {searchResults.map((product) => {
                      const isProductAvailable = canAddProductToCart(product);
                      return (
                        <button
                          key={product.id}
                          type="button"
                          onClick={() => handleAddProduct(product)}
                          disabled={!isOpen || isPosBusy || !isProductAvailable}
                          className="rounded-[24px] border border-border/70 bg-background px-4 py-4 text-left shadow-[0_12px_24px_rgba(196,188,222,0.08)] transition hover:-translate-y-0.5 hover:bg-secondary/40 disabled:opacity-50"
                        >
                          <p className="font-semibold text-foreground">{product.name}</p>
                          <p className="mt-1 text-sm text-muted-foreground">Tienda: {product.collaboratorName}</p>
                          <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
                            <span>SKU: {product.sku}</span>
                            <span className={product.stock <= 0 ? "font-semibold text-rose-600" : undefined}>
                              Stock: {product.stock}
                            </span>
                          </div>
                          <p className="mt-3 text-base font-semibold text-slate-900">{formatMoney(product.salePrice)}</p>
                          {!isProductAvailable ? (
                            <p className="mt-2 text-xs font-semibold text-rose-600">
                              {product.stock <= 0 ? "Sin stock disponible" : "Ya agregaste todo el stock disponible"}
                            </p>
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="mt-4">
                    <EmptyState
                      title="Tu caja esta lista para recibir productos"
                      description="Escribe un nombre, SKU o codigo de barras para comenzar a armar la venta."
                    />
                  </div>
                )}
              </div>
            </div>

            <div className="space-y-5">
              <div className="rounded-[30px] border border-violet-100 bg-[linear-gradient(180deg,rgba(255,248,252,0.98),rgba(246,243,255,0.96))] p-6 shadow-[0_18px_45px_rgba(186,168,223,0.15)]">
                <p className="text-xs font-semibold uppercase tracking-[0.3em] text-violet-500">TOTAL A COBRAR</p>
                <p className="mt-4 text-5xl font-black tracking-tight text-slate-900">{formatMoney(activeSale.totalAmount)}</p>
                <div className="mt-6 grid gap-2 text-sm">
                  <BreakdownRow label="Neto" value={formatMoney(activeSale.netAmount)} />
                  <BreakdownRow label="IVA" value={formatMoney(activeSale.ivaAmount)} />
                  <BreakdownRow label="Total" value={formatMoney(activeSale.totalAmount)} />
                </div>
              </div>

              <div className="soft-subtle-surface p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Carrito</h3>
                    <p className="text-sm text-muted-foreground">Ajusta cantidades, elimina productos y valida el cobro.</p>
                  </div>
                  <span className="rounded-full bg-violet-50 px-3 py-1 text-xs font-semibold text-violet-700">
                    {activeSale.items.reduce((total, item) => total + item.quantity, 0)} producto
                    {activeSale.items.reduce((total, item) => total + item.quantity, 0) === 1 ? "" : "s"}
                  </span>
                </div>

                {activeSale.items.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="Aun no agregas productos"
                      description="Escanea o busca un producto para comenzar la venta."
                    />
                  </div>
                ) : (
                  <div className="mt-4 space-y-3">
                    {activeSale.items.map((item) => (
                      <article
                        key={item.id}
                        className="rounded-[24px] border border-border/70 bg-background px-4 py-4 shadow-[0_10px_22px_rgba(196,188,222,0.08)]"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-semibold text-foreground">{item.productName}</p>
                            <p className="mt-1 text-sm text-muted-foreground">{item.collaboratorName ?? "Sin Tienda"}</p>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {item.manualEntry
                                ? item.appliedPromotionName ?? "Retiro por cobrar"
                                : item.promotionApplied
                                  ? item.appliedPromotionName ?? "Promocion aplicada"
                                  : "Sin promocion"}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() => removeItemMutation.mutate(item.id)}
                            disabled={!isOpen || isPosBusy}
                            className="rounded-full border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700 disabled:opacity-50"
                          >
                            Eliminar
                          </button>
                        </div>

                        <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
                          {item.manualEntry ? (
                            <span className="rounded-full border border-amber-200 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-700">
                              Retiro precargado
                            </span>
                          ) : (
                            <div className="inline-flex items-center rounded-full border border-border bg-secondary/40 p-1">
                              <button
                                type="button"
                                aria-label={`Disminuir cantidad de ${item.productName}`}
                                onClick={() => {
                                  if (item.quantity > 1) {
                                    updateItemMutation.mutate({ itemId: item.id, quantity: item.quantity - 1 });
                                  }
                                }}
                                disabled={!isOpen || isPosBusy || item.quantity <= 1}
                                className="rounded-full px-3 py-2 text-sm font-semibold text-foreground disabled:opacity-40"
                              >
                                -
                              </button>
                              <span className="min-w-10 text-center text-sm font-semibold text-foreground">{item.quantity}</span>
                              <button
                                type="button"
                                aria-label={`Aumentar cantidad de ${item.productName}`}
                                onClick={() => updateItemMutation.mutate({ itemId: item.id, quantity: item.quantity + 1 })}
                                disabled={
                                  !isOpen ||
                                  isPosBusy ||
                                  (item.availableStock != null && item.quantity >= item.availableStock)
                                }
                                className="rounded-full px-3 py-2 text-sm font-semibold text-foreground disabled:opacity-40"
                              >
                                +
                              </button>
                            </div>
                          )}

                          <div className="text-right">
                            <p className="text-xs text-muted-foreground">Precio unitario</p>
                            <p className="font-medium text-foreground">{formatMoney(item.baseUnitPrice)}</p>
                          </div>
                          <div className="text-right">
                            <p className="text-xs text-muted-foreground">Total cliente</p>
                            <p className="text-lg font-semibold text-slate-900">{formatMoney(item.totalClientAmount)}</p>
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>

              <div className="soft-subtle-surface space-y-4 p-5">
                <label className="grid gap-2 text-sm">
                  <span className="font-medium text-foreground">Metodo de pago</span>
                  <select
                    value={activeSale.paymentMethod}
                    disabled={!isOpen || isPosBusy}
                    onChange={(event) => paymentMethodMutation.mutate(event.target.value as PosSale["paymentMethod"])}
                    className="rounded-2xl border border-input bg-background px-4 py-3"
                  >
                    <option value="CASH">Efectivo</option>
                    <option value="DEBITO">Debito</option>
                    <option value="CREDIT">Credito</option>
                    <option value="TRANSFER">Transferencia</option>
                  </select>
                </label>

                {isCashPayment ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="grid gap-2 text-sm">
                      <span className="font-medium text-foreground">Monto recibido</span>
                      <input
                        inputMode="numeric"
                        value={amountReceived}
                        disabled={!isOpen || isPosBusy}
                        onChange={(event) => setAmountReceived(event.target.value.replace(/[^\d]/g, ""))}
                        className="rounded-2xl border border-input bg-background px-4 py-3"
                        placeholder="Ingresa el efectivo recibido"
                      />
                    </label>

                    <div className="rounded-[22px] border border-emerald-100 bg-emerald-50/70 px-4 py-3">
                      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-emerald-600">Vuelto</p>
                      <p className="mt-2 text-2xl font-black text-emerald-900">{formatMoney(changeAmount)}</p>
                      {isCashAmountInsufficient ? (
                        <p className="mt-2 text-xs font-medium text-rose-600">
                          El monto recibido debe cubrir el total a cobrar.
                        </p>
                      ) : (
                        <p className="mt-2 text-xs text-muted-foreground">
                          El vuelto se calcula automaticamente al cobrar en efectivo.
                        </p>
                      )}
                    </div>
                  </div>
                ) : null}

                <div className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={() => recalculateMutation.mutate()}
                    disabled={!isOpen || isPosBusy}
                    className="rounded-2xl border border-border px-4 py-3 text-sm font-semibold disabled:opacity-50"
                  >
                    {recalculateMutation.isPending ? "Recalculando..." : "Recalcular"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsConfirmModalOpen(true)}
                    disabled={!isOpen || isPosBusy || activeSale.items.length === 0 || isCashAmountInsufficient}
                    className="rounded-2xl bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    Confirmar venta
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={isPosCloseConfirmOpen}
        onClose={() => setIsPosCloseConfirmOpen(false)}
        title="Cancelar venta en curso"
        description="Esta venta sigue abierta. Si la cancelas, quedara anulada y no podras retomarla."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setIsPosCloseConfirmOpen(false)}
              className="rounded-2xl border border-border px-4 py-2 text-sm font-semibold"
            >
              Seguir vendiendo
            </button>
            <button
              type="button"
              onClick={() => cancelActiveSaleMutation.mutate()}
              disabled={cancelActiveSaleMutation.isPending}
              className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 disabled:opacity-50"
            >
              {cancelActiveSaleMutation.isPending ? "Cancelando..." : "Cancelar venta"}
            </button>
          </div>
        }
      >
        <p className="text-sm text-muted-foreground">
          Si prefieres retomarla despues, elige <span className="font-semibold text-foreground">Seguir vendiendo</span> y
          manten la venta abierta.
        </p>
      </Modal>

      <Modal
        open={isConfirmModalOpen}
        onClose={() => setIsConfirmModalOpen(false)}
        title="Confirmar venta"
        description="La venta quedara registrada y no se podra editar despues."
        footer={
          <div className="flex justify-end gap-3">
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
              {formatMoney(activeSale?.totalAmount ?? 0)}
            </p>
          </div>
          <p className="text-sm text-muted-foreground">Deseas confirmar esta venta?</p>
        </div>
      </Modal>
    </PosLauncherContext.Provider>
  );
}

export function usePosLauncher() {
  const context = useContext(PosLauncherContext);
  if (!context) {
    throw new Error("usePosLauncher must be used within PosLauncherProvider.");
  }
  return context;
}

function BreakdownRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-semibold text-slate-900">{value}</span>
    </div>
  );
}

function StatusBadge({ status }: { status: PosSaleSummary["status"] }) {
  const styleMap = {
    OPEN: "bg-violet-50 text-violet-700 border-violet-200",
    CONFIRMED: "bg-emerald-50 text-emerald-700 border-emerald-200",
    CANCELLED: "bg-rose-50 text-rose-700 border-rose-200",
  } as const;

  const labelMap = {
    OPEN: "Abierta",
    CONFIRMED: "Confirmada",
    CANCELLED: "Anulada",
  } as const;

  return (
    <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${styleMap[status]}`}>
      {labelMap[status]}
    </span>
  );
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
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
