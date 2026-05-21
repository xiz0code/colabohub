import { type ReactNode, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { usePosLauncher } from "@/features/sales/components/PosLauncherProvider";
import {
  cancelPosSale,
  editPosSale,
  getPosSale,
  listPosSales,
  type PosSale,
  type PosSaleSummary,
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
  const { openPos, openPosWithSale, isOpening } = usePosLauncher();
  const [detailSaleId, setDetailSaleId] = useState<number | null>(null);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  const activeMarketName = user?.activeMarketName ?? "tu Espacio";
  const canOperatePos = (primaryRole === "ADMIN_MARKET" || primaryRole === "SELLER") && Boolean(user?.activeMarketId);

  const salesQuery = useQuery({
    queryKey: ["pos", "sales"],
    queryFn: listPosSales,
    enabled: canOperatePos,
  });

  const detailSaleQuery = useQuery({
    queryKey: ["pos", "sales", detailSaleId],
    queryFn: () => getPosSale(detailSaleId!),
    enabled: detailSaleId !== null,
  });

  const openDetailMutation = useMutation({
    mutationFn: (saleId: number) => getPosSale(saleId),
    onSuccess: (sale) => {
      queryClient.setQueryData(["pos", "sales", sale.id], sale);
      setDetailSaleId(sale.id);
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cargar el detalle de la venta.") }),
  });

  const cancelMutation = useMutation({
    mutationFn: () => cancelPosSale(detailSaleQuery.data!.id, cancelReason),
    onSuccess: (sale) => {
      queryClient.setQueryData(["pos", "sales", sale.id], sale);
      void queryClient.invalidateQueries({ queryKey: ["pos", "sales"] });
      void queryClient.invalidateQueries({ queryKey: ["pos", "sales", sale.id] });
      setIsCancelModalOpen(false);
      setCancelReason("");
      setFeedback({ kind: "success", message: "Venta anulada y stock restaurado correctamente." });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible anular la venta.") }),
  });

  const editMutation = useMutation({
    mutationFn: (saleId: number) => editPosSale(saleId),
    onSuccess: (sale) => {
      queryClient.setQueryData(["pos", "sales", sale.id], sale);
      void queryClient.invalidateQueries({ queryKey: ["pos", "sales"] });
      setDetailSaleId(null);
      setFeedback({ kind: "success", message: "Venta cargada en la caja para edición." });
      openPosWithSale(sale);
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible abrir la venta para edición.") }),
  });

  const saleForDetail = detailSaleQuery.data ?? null;

  if (!canOperatePos) {
    return (
      <section>
        <PageHeader
          title="Ventas"
          description="El POS esta disponible solo para Administradores de Espacio y vendedores con un Espacio activo."
        />
        <div className="soft-surface p-8">
          <EmptyState
            title="No tienes acceso operativo al POS"
            description="Solicita acceso como Administrador de Espacio o vendedor si necesitas registrar ventas."
          />
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title="Ventas"
        description="Administra tu caja, revisa el historial y anula ventas confirmadas con trazabilidad completa."
        eyebrow="POS SaaS"
        actions={
          <button
            type="button"
            onClick={openPos}
            disabled={isOpening}
            className="rounded-[20px] bg-[linear-gradient(135deg,rgba(163,128,255,0.95),rgba(255,153,194,0.92))] px-6 py-3 text-sm font-semibold text-white shadow-[0_16px_30px_rgba(179,146,225,0.28)] transition hover:scale-[1.03] disabled:opacity-50"
          >
            {isOpening ? "Preparando venta..." : "Nueva venta"}
          </button>
        }
      />

      {feedback ? (
        <div className="fixed right-6 top-24 z-40 max-w-md">
          <FeedbackMessage kind={feedback.kind} message={feedback.message} />
        </div>
      ) : null}

      <div className="soft-surface p-6">
        <div className="flex flex-col gap-3 border-b border-border/60 pb-5 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-xl font-semibold text-foreground">Ventas de {activeMarketName}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Cada nueva venta abre o reutiliza automaticamente la venta en curso de tu Espacio.
            </p>
          </div>
          <div className="soft-subtle-surface rounded-[22px] px-4 py-3 text-sm">
            <span className="text-muted-foreground">Ventas visibles: </span>
            <span className="font-semibold text-foreground">{salesQuery.data?.length ?? 0}</span>
          </div>
        </div>

        {salesQuery.isLoading ? (
          <div className="mt-5">
            <FeedbackMessage kind="info" message="Cargando historial de ventas..." />
          </div>
        ) : null}
        {salesQuery.isError ? (
          <div className="mt-5">
            <FeedbackMessage kind="error" message={getErrorMessage(salesQuery.error, "No fue posible cargar las ventas.")} />
          </div>
        ) : null}

        {salesQuery.data && salesQuery.data.length > 0 ? (
          <div className="soft-table mt-6">
            <table>
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Estado</th>
                  <th>Neto</th>
                  <th>IVA</th>
                  <th>Total</th>
                  <th>Metodo pago</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {salesQuery.data.map((sale) => (
                  <tr key={sale.id}>
                    <td>
                      <p className="font-medium">{formatDateTime(sale.dateTime)}</p>
                      <p className="text-xs text-muted-foreground">
                        {sale.saleNumber}
                        {sale.sellerName ? ` | ${sale.sellerName}` : ""}
                      </p>
                    </td>
                    <td>
                      <StatusBadge status={sale.status} />
                    </td>
                    <td>{formatMoney(sale.netAmount)}</td>
                    <td>{formatMoney(sale.ivaAmount)}</td>
                    <td className="font-semibold">{formatMoney(sale.totalAmount)}</td>
                    <td>{formatPaymentMethodLabel(sale.paymentMethod)}</td>
                    <td>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => openDetailMutation.mutate(sale.id)}
                          className="rounded-full border border-border px-3 py-1.5 text-xs font-semibold"
                        >
                          Ver detalle
                        </button>
                        {sale.status === "CONFIRMED" ? (
                          <button
                            type="button"
                            onClick={() => editMutation.mutate(sale.id)}
                            className="rounded-full border border-violet-200 px-3 py-1.5 text-xs font-semibold text-violet-700"
                          >
                            Editar
                          </button>
                        ) : null}
                        {sale.status === "CONFIRMED" ? (
                          <button
                            type="button"
                            onClick={() =>
                              openDetailMutation.mutate(sale.id, {
                                onSuccess: () => setIsCancelModalOpen(true),
                              })
                            }
                            className="rounded-full border border-rose-200 px-3 py-1.5 text-xs font-semibold text-rose-700"
                          >
                            Anular venta
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !salesQuery.isLoading && (
            <div className="mt-6">
              <EmptyState
                title="Todavia no hay ventas registradas"
                description="Abre una nueva venta para comenzar a cobrar y el historial aparecera aqui."
              />
            </div>
          )
        )}
      </div>

      <Modal
        open={detailSaleId !== null}
        onClose={() => setDetailSaleId(null)}
        title={saleForDetail ? `Detalle ${saleForDetail.saleNumber}` : "Detalle de venta"}
        description="Revisa productos, promociones, comisiones y totales registrados."
        maxWidthClassName="max-w-6xl"
        bodyClassName="overflow-auto"
      >
        {detailSaleQuery.isLoading ? (
          <FeedbackMessage kind="info" message="Cargando detalle de la venta..." />
        ) : !saleForDetail ? (
          <EmptyState title="No pudimos cargar esta venta" description="Intenta nuevamente desde el listado principal." />
        ) : (
          <div className="space-y-5">
            <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
              <DetailCard label="Estado" value={<StatusBadge status={saleForDetail.status} />} />
              <DetailCard
                label={saleForDetail.confirmedAt ? "Fecha y hora" : "Inicio venta"}
                value={formatDateTime(saleForDetail.confirmedAt ?? saleForDetail.openedAt)}
              />
              <DetailCard label="Metodo pago" value={formatPaymentMethodLabel(saleForDetail.paymentMethod)} />
              <DetailCard label="Neto" value={formatMoney(saleForDetail.netAmount)} />
              <DetailCard label="IVA" value={formatMoney(saleForDetail.ivaAmount)} />
              <DetailCard label="Total" value={formatMoney(saleForDetail.totalAmount)} />
            </div>

            {saleForDetail.status === "CANCELLED" ? (
              <FeedbackMessage
                kind="info"
                message={`Venta anulada${saleForDetail.cancelledBy ? ` por ${saleForDetail.cancelledBy}` : ""}${saleForDetail.cancellationReason ? `: ${saleForDetail.cancellationReason}` : "."}`}
              />
            ) : null}

            {saleForDetail.status === "CONFIRMED" ? (
              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={() => editMutation.mutate(saleForDetail.id)}
                  className="rounded-2xl border border-violet-200 px-4 py-2 text-sm font-semibold text-violet-700"
                >
                  Editar venta
                </button>
              </div>
            ) : null}

            <div className="soft-table">
              <table>
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Tienda</th>
                    <th>Cantidad</th>
                    <th>Precio</th>
                    <th>Promocion</th>
                    <th>UF</th>
                    <th>Comision fija</th>
                    <th>Comision variable</th>
                    <th>IVA comision</th>
                    <th>Total tienda</th>
                    <th>Total cliente</th>
                  </tr>
                </thead>
                <tbody>
                  {saleForDetail.items.map((item) => (
                    <tr key={item.id}>
                      <td>{item.productName}</td>
                      <td>{item.collaboratorName ?? "Sin Tienda"}</td>
                      <td>{item.quantity}</td>
                      <td>{formatMoney(item.baseUnitPrice)}</td>
                      <td>{item.promotionApplied ? item.appliedPromotionName ?? "Si" : "No"}</td>
                      <td>{item.ufValue !== null ? formatUfValue(item.ufValue) : "No aplica"}</td>
                      <td>{formatMoney(item.commission1Amount)}</td>
                      <td>{formatMoney(item.commission2Amount)}</td>
                      <td>{formatMoney(item.commissionIvaAmount)}</td>
                      <td>{formatMoney(item.totalCollaboratorAmount)}</td>
                      <td>{formatMoney(item.totalClientAmount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={isCancelModalOpen}
        onClose={() => {
          setIsCancelModalOpen(false);
          setCancelReason("");
        }}
        title="Anular venta"
        description="La venta quedara anulada, se restaurara el stock y se registrara el motivo."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => {
                setIsCancelModalOpen(false);
                setCancelReason("");
              }}
              className="rounded-2xl border border-border px-4 py-2 text-sm font-semibold"
            >
              Volver
            </button>
            <button
              type="button"
              onClick={() => cancelMutation.mutate()}
              disabled={cancelMutation.isPending || cancelReason.trim().length === 0 || !detailSaleQuery.data}
              className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 disabled:opacity-50"
            >
              {cancelMutation.isPending ? "Anulando..." : "Confirmar anulacion"}
            </button>
          </div>
        }
      >
        <label className="grid gap-2 text-sm">
          <span>Motivo de anulacion</span>
          <textarea
            value={cancelReason}
            onChange={(event) => setCancelReason(event.target.value)}
            rows={4}
            className="rounded-2xl border border-input bg-background px-3 py-3"
            placeholder="Describe brevemente por que se anula la venta"
          />
        </label>
      </Modal>
    </section>
  );
}

function DetailCard({ label, value }: { label: string; value: ReactNode }) {
  return (
    <article className="soft-subtle-surface rounded-[24px] p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <div className="mt-2 text-lg font-semibold text-foreground">{value}</div>
    </article>
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

  return <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${styleMap[status]}`}>{labelMap[status]}</span>;
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

function formatPaymentMethodLabel(paymentMethod: PosSale["paymentMethod"] | null) {
  if (!paymentMethod) {
    return "Pendiente";
  }

  const labelMap = {
    CASH: "Efectivo",
    CREDIT: "Credito",
    DEBITO: "Debito",
    TRANSFER: "Transferencia",
  } as const;

  return labelMap[paymentMethod];
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
