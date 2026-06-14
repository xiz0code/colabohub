import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  closeDaily,
  closeMonthly,
  previewDailyClosing,
  previewMonthlyClosing,
  type DailyClosing,
  type MonthlyClosing,
} from "@/features/closings/api/closingApi";
import { listMarkets } from "@/features/markets/api/marketApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";
import { downloadCsv } from "@/shared/lib/files/downloadCsv";

type ClosingMode = "daily" | "monthly";

function today() {
  return new Date().toISOString().slice(0, 10);
}

function currentMonth() {
  return new Date().toISOString().slice(0, 7);
}

export function DailyClosingPage({ defaultMode = "daily" }: { defaultMode?: ClosingMode }) {
  const { roles, user, primaryRole } = useSession();
  const [mode, setMode] = useState<ClosingMode>(defaultMode);
  const [selectedMarketId, setSelectedMarketId] = useState(user?.activeMarketId ? String(user.activeMarketId) : "");
  const [closingDate, setClosingDate] = useState(today());
  const [closingMonth, setClosingMonth] = useState(currentMonth());
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);

  const marketsQuery = useQuery({
    queryKey: ["markets"],
    queryFn: listMarkets,
  });

  const marketOptions = useMemo(() => {
    const allMarkets = marketsQuery.data ?? [];
    if (primaryRole !== "ADMIN_MARKET") {
      return allMarkets;
    }

    return user?.activeMarketId ? allMarkets.filter((market) => market.id === user.activeMarketId) : allMarkets;
  }, [marketsQuery.data, primaryRole, user?.activeMarketId]);

  const effectiveMarketId = primaryRole === "ADMIN_MARKET" && user?.activeMarketId ? String(user.activeMarketId) : selectedMarketId;
  const canCloseDay = roles.some((role) => ["ADMIN_SYSTEM", "ADMIN_MARKET"].includes(role));

  const dailyClosingQuery = useQuery({
    queryKey: ["daily-closing-preview", effectiveMarketId, closingDate],
    queryFn: () => previewDailyClosing(Number(effectiveMarketId), closingDate),
    enabled: mode === "daily" && effectiveMarketId.length > 0,
    retry: false,
  });

  const monthlyClosingQuery = useQuery({
    queryKey: ["monthly-closing-preview", effectiveMarketId, closingMonth],
    queryFn: () => previewMonthlyClosing(Number(effectiveMarketId), closingMonth),
    enabled: mode === "monthly" && effectiveMarketId.length > 0,
    retry: false,
  });

  const closeDailyMutation = useMutation({
    mutationFn: () => closeDaily(Number(effectiveMarketId), closingDate),
    onSuccess: () => {
      setFeedback({ kind: "success", message: "Cierre diario guardado y actualizado correctamente." });
      void dailyClosingQuery.refetch();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible guardar el cierre diario.") });
    },
  });

  const closeMonthlyMutation = useMutation({
    mutationFn: () => closeMonthly(Number(effectiveMarketId), closingMonth),
    onSuccess: () => {
      setFeedback({ kind: "success", message: "Cierre mensual guardado y actualizado correctamente." });
      void monthlyClosingQuery.refetch();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible guardar el cierre mensual.") });
    },
  });

  const currentDailyClosing = dailyClosingQuery.data ?? null;
  const currentMonthlyClosing = monthlyClosingQuery.data ?? null;
  const isAdminMarket = primaryRole === "ADMIN_MARKET";
  const selectedMarketName =
    marketOptions.find((market) => String(market.id) === effectiveMarketId)?.name ?? user?.activeMarketName ?? "este Espacio";
  const confirmDescription =
    mode === "daily"
      ? `Se guardara o actualizara el cierre diario de ${selectedMarketName} para la fecha ${closingDate}.`
      : `Se guardara o actualizara el cierre mensual de ${selectedMarketName} para el periodo ${closingMonth}.`;
  const canTriggerClosing =
    effectiveMarketId.length > 0 &&
    !closeDailyMutation.isPending &&
    !closeMonthlyMutation.isPending &&
    canCloseDay;

  useEffect(() => {
    setMode(defaultMode);
  }, [defaultMode]);

  return (
    <section>
      <PageHeader
        title="Cierres"
        description="Consolida el resultado diario o mensual de tu Espacio con una vista clara, exportable y lista para control operativo."
        eyebrow="Centro de cierre"
      />

      <div className="mb-6 flex flex-wrap gap-3">
        <Link
          to="/closings"
          className={[
            "rounded-full px-4 py-2 text-sm font-semibold transition",
            mode === "daily"
              ? "bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-white shadow-[0_10px_24px_rgba(187,156,232,0.22)]"
              : "border border-white/90 bg-white/75 text-muted-foreground shadow-sm hover:text-foreground",
          ].join(" ")}
        >
          Cierre diario
        </Link>
        <Link
          to="/closings/monthly"
          className={[
            "rounded-full px-4 py-2 text-sm font-semibold transition",
            mode === "monthly"
              ? "bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-white shadow-[0_10px_24px_rgba(187,156,232,0.22)]"
              : "border border-white/90 bg-white/75 text-muted-foreground shadow-sm hover:text-foreground",
          ].join(" ")}
        >
          Cierre mensual
        </Link>
      </div>

      <div className="soft-surface mb-6 overflow-hidden p-6 md:p-7">
        <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-4">
            <div>
              <h2 className="text-2xl font-semibold tracking-tight">
                {mode === "daily" ? "Cierre diario del Espacio" : "Cierre mensual del Espacio"}
              </h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
                {mode === "daily"
                  ? "Usa esta vista para consolidar la jornada, revisar ventas, comisiones y neto por Tienda antes de cerrar el dia."
                  : "Usa esta vista para consolidar el mes, revisar IVA, facturacion y resultado por Tienda con un cierre exportable."}
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <ClosingHeroCard
                label="Espacio activo"
                value={effectiveMarketId ? selectedMarketName : "Sin seleccionar"}
                tone="violet"
              />
              <ClosingHeroCard
                label={mode === "daily" ? "Fecha de cierre" : "Mes de cierre"}
                value={mode === "daily" ? closingDate : closingMonth}
                tone="pink"
              />
              <ClosingHeroCard
                label="Accion preparada"
                value={mode === "daily" ? "Consolidar dia" : "Consolidar mes"}
                tone="amber"
              />
            </div>
          </div>

          <div className="rounded-[28px] border border-white/85 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,245,250,0.95))] p-5 shadow-[0_18px_36px_rgba(186,170,211,0.12)]">
            <div className="mb-4">
              <h3 className="text-lg font-semibold">Parametros de cierre</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Define el Espacio y el periodo que quieres consultar o consolidar.
              </p>
            </div>

            <div className="grid gap-4">
              <label className="grid gap-2 text-sm">
                <span>Espacio</span>
                <select
                  value={effectiveMarketId}
                  onChange={(event) => {
                    setSelectedMarketId(event.target.value);
                    setFeedback(null);
                  }}
                  disabled={isAdminMarket}
                  className="rounded-2xl border border-input bg-background/80 px-3 py-2.5 disabled:opacity-70"
                >
                  <option value="">Selecciona un Espacio</option>
                  {marketOptions.map((market) => (
                    <option key={market.id} value={market.id}>
                      {market.name}
                    </option>
                  ))}
                </select>
              </label>

              {mode === "daily" ? (
                <label className="grid gap-2 text-sm">
                  <span>Fecha</span>
                  <input
                    type="date"
                    value={closingDate}
                    onChange={(event) => setClosingDate(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
              ) : (
                <label className="grid gap-2 text-sm">
                  <span>Mes</span>
                  <input
                    type="month"
                    value={closingMonth}
                    onChange={(event) => setClosingMonth(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
              )}

              <div className="grid gap-3 sm:grid-cols-2">
                {mode === "daily" ? (
                  <>
                    <button
                      type="button"
                      disabled={!canTriggerClosing}
                      onClick={() => setConfirmOpen(true)}
                      className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
                    >
                      {!canCloseDay ? "Solo lectura" : closeDailyMutation.isPending ? "Guardando..." : "Guardar cierre diario"}
                    </button>
                    <button
                      type="button"
                      disabled={effectiveMarketId.length === 0 || dailyClosingQuery.isFetching || closeDailyMutation.isPending}
                      onClick={() => {
                        setFeedback(null);
                        void dailyClosingQuery.refetch();
                      }}
                      className="rounded-full border border-white/90 bg-white/75 px-4 py-3 text-sm font-semibold shadow-sm disabled:opacity-50"
                    >
                      Actualizar vista previa
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      disabled={!canTriggerClosing}
                      onClick={() => setConfirmOpen(true)}
                      className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
                    >
                      {!canCloseDay ? "Solo lectura" : closeMonthlyMutation.isPending ? "Guardando..." : "Guardar cierre mensual"}
                    </button>
                    <button
                      type="button"
                      disabled={effectiveMarketId.length === 0 || monthlyClosingQuery.isFetching || closeMonthlyMutation.isPending}
                      onClick={() => {
                        setFeedback(null);
                        void monthlyClosingQuery.refetch();
                      }}
                      className="rounded-full border border-white/90 bg-white/75 px-4 py-3 text-sm font-semibold shadow-sm disabled:opacity-50"
                    >
                      Actualizar vista previa
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[320px_1fr]">
        <div className="space-y-4">
          <ClosingGuideCard
            title={mode === "daily" ? "Antes de cerrar el dia" : "Antes de cerrar el mes"}
            items={
              mode === "daily"
                ? [
                    "Confirma que las ventas del POS ya esten cerradas.",
                    "Revisa que el Espacio seleccionado corresponda a la jornada correcta.",
                    "Descarga el CSV despues del cierre para respaldo operativo.",
                  ]
                : [
                    "Verifica el mes seleccionado antes de consolidar.",
                    "Revisa IVA y estado de facturacion por Tienda.",
                    "Descarga el CSV mensual para control administrativo.",
                  ]
            }
          />
          <ClosingGuideCard
            title="Resultado esperado"
            items={
              mode === "daily"
                ? [
                    "Resumen por Tienda con ventas, items, comision y neto.",
                    "Registro persistido del cierre para esa fecha.",
                    "Correo de cierre disparado segun configuracion del entorno.",
                  ]
                : [
                    "Resumen mensual por Tienda con IVA total e IVA a pagar.",
                    "Registro persistido del mes consultado.",
                    "Base lista para exportacion y control financiero.",
                  ]
            }
          />
        </div>

        <div className="space-y-4">
          {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}
          {marketsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando Espacios..." /> : null}
          {marketsQuery.isError ? (
            <FeedbackMessage kind="error" message={getErrorMessage(marketsQuery.error, "No fue posible cargar los Espacios.")} />
          ) : null}

          {!effectiveMarketId ? (
            <EmptyState
              title="Selecciona un Espacio"
              description="Elige un Espacio y un periodo para consultar o generar cierres."
            />
          ) : mode === "daily" ? (
            <DailyClosingPanel
              query={dailyClosingQuery}
              closing={currentDailyClosing}
            />
          ) : (
            <MonthlyClosingPanel
              query={monthlyClosingQuery}
              closing={currentMonthlyClosing}
            />
          )}
        </div>
      </div>

      <Modal
        open={confirmOpen}
        title={mode === "daily" ? "Confirmar cierre diario" : "Confirmar cierre mensual"}
        description={confirmDescription}
        onClose={() => {
          if (!closeDailyMutation.isPending && !closeMonthlyMutation.isPending) {
            setConfirmOpen(false);
          }
        }}
        closeOnEscape={!closeDailyMutation.isPending && !closeMonthlyMutation.isPending}
        closeOnOverlayClick={!closeDailyMutation.isPending && !closeMonthlyMutation.isPending}
        footer={
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={() => setConfirmOpen(false)}
              disabled={closeDailyMutation.isPending || closeMonthlyMutation.isPending}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold shadow-sm disabled:opacity-50"
            >
              Volver
            </button>
            <button
              type="button"
              onClick={() => {
                if (mode === "daily") {
                  closeDailyMutation.mutate(undefined, {
                    onSettled: () => setConfirmOpen(false),
                  });
                  return;
                }

                closeMonthlyMutation.mutate(undefined, {
                  onSettled: () => setConfirmOpen(false),
                });
              }}
              disabled={closeDailyMutation.isPending || closeMonthlyMutation.isPending}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
            >
              {mode === "daily"
                ? closeDailyMutation.isPending
                  ? "Generando..."
                  : "Confirmar cierre diario"
                : closeMonthlyMutation.isPending
                  ? "Generando..."
                  : "Confirmar cierre mensual"}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <div className="rounded-[24px] border border-white/85 bg-white/70 p-4 shadow-sm">
            <p className="text-sm text-muted-foreground">Espacio</p>
            <p className="mt-2 text-base font-semibold">{selectedMarketName}</p>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-[24px] border border-white/85 bg-white/70 p-4 shadow-sm">
              <p className="text-sm text-muted-foreground">{mode === "daily" ? "Fecha de cierre" : "Mes de cierre"}</p>
              <p className="mt-2 text-base font-semibold">{mode === "daily" ? closingDate : closingMonth}</p>
            </div>
            <div className="rounded-[24px] border border-white/85 bg-white/70 p-4 shadow-sm">
              <p className="text-sm text-muted-foreground">Accion</p>
              <p className="mt-2 text-base font-semibold">
                {mode === "daily" ? "Guardar o actualizar ventas del dia" : "Guardar o actualizar ventas del mes"}
              </p>
            </div>
          </div>
          <FeedbackMessage
            kind="info"
            message={
              mode === "daily"
                ? "Al confirmar, el sistema persistira el resumen financiero diario y refrescara la vista automaticamente."
                : "Al confirmar, el sistema persistira o actualizara el cierre mensual y el detalle por Tienda para el periodo seleccionado."
            }
          />
        </div>
      </Modal>
    </section>
  );
}

function DailyClosingPanel({
  query,
  closing,
}: {
  query: ReturnType<typeof useQuery<DailyClosing>>;
  closing: DailyClosing | null;
}) {
  if (query.isLoading || query.isFetching) {
    return <FeedbackMessage kind="info" message="Calculando vista previa del cierre diario..." />;
  }

  if (query.isError && !closing) {
    return <FeedbackMessage kind="error" message={getErrorMessage(query.error, "No fue posible consultar el cierre diario.")} />;
  }

  if (!closing) {
    return null;
  }

  return (
    <>
      <div className="soft-surface p-6">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <span className="soft-chip mb-2 inline-flex">{closing.closedAt ? "Cierre guardado" : "Informe preliminar"}</span>
            <h2 className="text-lg font-semibold">{closing.marketName}</h2>
            <p className="text-sm text-muted-foreground">
              Fecha: {closing.closingDate} | {formatClosingStatus(closing.closedAt, closing.closedBy)}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="soft-chip">{closing.saleCount} venta(s)</span>
            <button
              type="button"
              onClick={() => {
                downloadCsv(buildDailyClosingFilename(closing), buildDailyClosingRows(closing));
              }}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
            >
              Descargar CSV
            </button>
          </div>
        </div>

        <div className="mt-5 grid gap-3 text-sm md:grid-cols-3">
          <MetricCard label="Ventas" value={formatMoney(closing.totalSalesAmount)} />
          <MetricCard label="Comisiones" value={formatMoney(closing.totalCommissionAmount)} />
          <MetricCard label="Total a recibir" value={formatMoney(closing.totalNetAmount)} />
        </div>

        <PaymentMethodBreakdown paymentMethods={closing.paymentMethods ?? []} />

        <div className="mt-5 grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
          <div className="rounded-[24px] border border-white/85 bg-white/70 p-5 shadow-sm">
            <h3 className="text-base font-semibold">Pulso del cierre</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Lectura rapida del peso relativo de cada Tienda dentro de la jornada consolidada.
            </p>
            <div className="mt-4 space-y-3">
              {closing.stores.map((store) => (
                <ClosingBar
                  key={store.storeId}
                  label={store.storeName}
                  amount={store.totalSalesAmount}
                  maxAmount={Math.max(...closing.stores.map((item) => item.totalSalesAmount), 1)}
                />
              ))}
            </div>
          </div>

          <div className="rounded-[24px] border border-white/85 bg-white/70 p-5 shadow-sm">
            <h3 className="text-base font-semibold">Estado del cierre</h3>
            <div className="mt-4 grid gap-3">
              <MetricCard label="Tiendas con movimiento" value={String(closing.stores.length)} />
              <MetricCard label="Venta promedio" value={formatMoney(closing.saleCount > 0 ? closing.totalSalesAmount / closing.saleCount : 0)} />
            </div>
          </div>
        </div>
      </div>

      <div className="soft-surface p-6">
        <h2 className="text-lg font-semibold">Desglose por Tienda</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Vista previa por Tienda. Al guardar, este mismo detalle queda registrado para el cierre.
        </p>

        {closing.stores.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              title="Sin tiendas en el cierre"
              description="No hay ventas confirmadas para esa fecha en el Espacio seleccionado."
            />
          </div>
        ) : (
          <div className="soft-table mt-4">
            <table>
              <thead>
                <tr>
                  <th>Tienda</th>
                  <th>Ventas</th>
                  <th>Items</th>
                  <th>Comision</th>
                  <th>Total a recibir</th>
                </tr>
              </thead>
              <tbody>
                {closing.stores.map((store) => (
                  <tr key={store.storeId}>
                    <td>
                      <p className="font-medium">{store.storeName}</p>
                      <p className="text-sm text-muted-foreground">{store.saleCount} venta(s)</p>
                    </td>
                    <td>{formatMoney(store.totalSalesAmount)}</td>
                    <td>{store.totalItems}</td>
                    <td>{formatMoney(store.totalCommissionAmount)}</td>
                    <td>{formatMoney(store.totalNetAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function MonthlyClosingPanel({
  query,
  closing,
}: {
  query: ReturnType<typeof useQuery<MonthlyClosing>>;
  closing: MonthlyClosing | null;
}) {
  if (query.isLoading || query.isFetching) {
    return <FeedbackMessage kind="info" message="Calculando vista previa del cierre mensual..." />;
  }

  if (query.isError && !closing) {
    return <FeedbackMessage kind="error" message={getErrorMessage(query.error, "No fue posible consultar el cierre mensual.")} />;
  }

  if (!closing) {
    return null;
  }

  return (
    <>
      <div className="soft-surface p-6">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <span className="soft-chip mb-2 inline-flex">{closing.closedAt ? "Cierre guardado" : "Informe preliminar"}</span>
            <h2 className="text-lg font-semibold">{closing.marketName}</h2>
            <p className="text-sm text-muted-foreground">
              Mes: {closing.closingMonth} | {formatClosingStatus(closing.closedAt, closing.closedBy)}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="soft-chip">{closing.saleCount} venta(s)</span>
            <button
              type="button"
              onClick={() => {
                downloadCsv(buildMonthlyClosingFilename(closing), buildMonthlyClosingRows(closing));
              }}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
            >
              Descargar CSV
            </button>
          </div>
        </div>

        <div className="mt-5 grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-5">
          <MetricCard label="Ventas" value={formatMoney(closing.totalSalesAmount)} />
          <MetricCard label="Comisiones" value={formatMoney(closing.totalCommissionAmount)} />
          <MetricCard label="Total a recibir" value={formatMoney(closing.totalNetAmount)} />
          <MetricCard label="IVA total" value={formatMoney(closing.totalIvaAmount)} />
          <MetricCard label="IVA a pagar" value={formatMoney(closing.totalIvaToPayAmount)} />
        </div>

        <PaymentMethodBreakdown paymentMethods={closing.paymentMethods ?? []} />

        <div className="mt-5 grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
          <div className="rounded-[24px] border border-white/85 bg-white/70 p-5 shadow-sm">
            <h3 className="text-base font-semibold">Peso de ventas por Tienda</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              Comparativo visual para detectar rapido que Tiendas movieron mas venta en el mes.
            </p>
            <div className="mt-4 space-y-3">
              {closing.collaborators.map((collaborator) => (
                <ClosingBar
                  key={collaborator.collaboratorUserId}
                  label={collaborator.collaboratorName}
                  amount={collaborator.totalSalesAmount}
                  maxAmount={Math.max(...closing.collaborators.map((item) => item.totalSalesAmount), 1)}
                />
              ))}
            </div>
          </div>

          <div className="rounded-[24px] border border-white/85 bg-white/70 p-5 shadow-sm">
            <h3 className="text-base font-semibold">Lectura del mes</h3>
            <div className="mt-4 grid gap-3">
              <MetricCard label="Tiendas incluidas" value={String(closing.collaborators.length)} />
              <MetricCard label="Ticket promedio" value={formatMoney(closing.saleCount > 0 ? closing.totalSalesAmount / closing.saleCount : 0)} />
            </div>
          </div>
        </div>
      </div>

      <div className="soft-surface p-6">
        <h2 className="text-lg font-semibold">Resumen por Tienda</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Usa esta vista previa mensual para revisar ventas, total a recibir e IVA a pagar por Tienda antes de guardar.
        </p>

        {closing.collaborators.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              title="Sin Tiendas en este cierre"
              description="No hubo ventas confirmadas para Tiendas en el periodo consultado."
            />
          </div>
        ) : (
          <div className="soft-table mt-4">
            <table>
              <thead>
                <tr>
                  <th>Tienda</th>
                  <th>Factura</th>
                  <th>Ventas</th>
                  <th>Items</th>
                  <th>Comision</th>
                  <th>Total a recibir</th>
                  <th>IVA total</th>
                  <th>IVA a pagar</th>
                </tr>
              </thead>
              <tbody>
                {closing.collaborators.map((collaborator) => (
                  <tr key={collaborator.collaboratorUserId}>
                    <td>
                      <p className="font-medium">{collaborator.collaboratorName}</p>
                      <p className="text-sm text-muted-foreground">{collaborator.collaboratorEmail}</p>
                    </td>
                    <td>{collaborator.factura ? "Si" : "No"}</td>
                    <td>{collaborator.saleCount}</td>
                    <td>{collaborator.totalItems}</td>
                    <td>{formatMoney(collaborator.totalCommissionAmount)}</td>
                    <td>{formatMoney(collaborator.totalNetAmount)}</td>
                    <td>{formatMoney(collaborator.totalIvaAmount)}</td>
                    <td>{formatMoney(collaborator.ivaToPayAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function ClosingHeroCard({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: "violet" | "pink" | "amber";
}) {
  const toneClass =
    tone === "violet"
      ? "from-[rgba(243,238,255,0.98)] to-[rgba(255,245,252,0.96)]"
      : tone === "pink"
        ? "from-[rgba(255,242,248,0.98)] to-[rgba(255,249,241,0.96)]"
        : "from-[rgba(255,249,238,0.98)] to-[rgba(255,244,251,0.96)]";

  return (
    <div className={`rounded-[24px] border border-white/85 bg-gradient-to-br ${toneClass} p-4 shadow-sm`}>
      <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">{label}</p>
      <p className="mt-3 text-lg font-semibold tracking-tight text-foreground">{value}</p>
    </div>
  );
}

function ClosingGuideCard({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="soft-surface p-5">
      <h3 className="text-base font-semibold">{title}</h3>
      <div className="mt-4 space-y-3">
        {items.map((item) => (
          <div key={item} className="flex items-start gap-3 rounded-[18px] border border-white/80 bg-white/65 px-4 py-3 shadow-sm">
            <span className="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-xs font-bold text-white">
              •
            </span>
            <p className="text-sm leading-6 text-muted-foreground">{item}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function ClosingBar({ label, amount, maxAmount }: { label: string; amount: number; maxAmount: number }) {
  const width = maxAmount > 0 ? Math.max((amount / maxAmount) * 100, 8) : 0;

  return (
    <div className="grid gap-2 md:grid-cols-[180px_1fr_120px] md:items-center">
      <span className="text-sm font-medium text-foreground">{label}</span>
      <div className="h-3 overflow-hidden rounded-full bg-secondary/70">
        <div
          className="h-full rounded-full bg-[linear-gradient(135deg,rgba(163,128,255,0.96),rgba(255,153,194,0.92))]"
          style={{ width: `${width}%` }}
        />
      </div>
      <span className="text-sm font-semibold text-foreground md:text-right">{formatMoney(amount)}</span>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[22px] border border-white/85 bg-white/70 p-4 shadow-sm">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-2 text-lg font-semibold">{value}</p>
    </div>
  );
}

function PaymentMethodBreakdown({
  paymentMethods,
}: {
  paymentMethods: NonNullable<DailyClosing["paymentMethods"]>;
}) {
  const total = paymentMethods.reduce((sum, paymentMethod) => sum + paymentMethod.totalSalesAmount, 0);

  return (
    <div className="mt-5 rounded-[24px] border border-white/85 bg-white/70 p-5 shadow-sm">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h3 className="text-base font-semibold">Cuadratura por medio de pago</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Recuento de ventas confirmadas para revisar caja, tarjetas y transferencias del periodo.
          </p>
        </div>
        <span className="text-sm font-semibold text-foreground">{formatMoney(total)}</span>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {paymentMethods.map((paymentMethod) => (
          <div
            key={paymentMethod.paymentMethod}
            className="rounded-[20px] border border-white/80 bg-white/75 p-4 shadow-sm"
          >
            <p className="text-sm text-muted-foreground">{formatPaymentMethodLabel(paymentMethod.paymentMethod)}</p>
            <p className="mt-2 text-lg font-semibold">{formatMoney(paymentMethod.totalSalesAmount)}</p>
            <p className="mt-1 text-xs text-muted-foreground">{paymentMethod.saleCount} venta(s)</p>
          </div>
        ))}
      </div>
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

function formatPaymentMethodLabel(paymentMethod: string) {
  switch (paymentMethod) {
    case "CASH":
      return "Efectivo";
    case "DEBITO":
      return "Debito";
    case "CREDIT":
      return "Credito";
    case "TRANSFER":
      return "Transferencia";
    default:
      return paymentMethod;
  }
}

function formatClosingStatus(closedAt: string | null, closedBy: string | null) {
  if (!closedAt) {
    return "Vista previa sin guardar";
  }

  return `Guardado por: ${closedBy || "sistema"}`;
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

function slugify(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

function buildDailyClosingFilename(closing: DailyClosing) {
  return `cierre-diario-${slugify(closing.marketName)}-${closing.closingDate}.csv`;
}

function buildMonthlyClosingFilename(closing: MonthlyClosing) {
  return `cierre-mensual-${slugify(closing.marketName)}-${closing.closingMonth}.csv`;
}

function buildDailyClosingRows(closing: DailyClosing) {
  return closing.stores.map((store) => ({
    espacio: closing.marketName,
    fecha_cierre: closing.closingDate,
    cerrado_por: closing.closedBy || "Vista previa",
    tienda: store.storeName,
    ventas: store.saleCount,
    items: store.totalItems,
    monto_ventas: store.totalSalesAmount,
    comision: store.totalCommissionAmount,
    neto: store.totalNetAmount,
  }));
}

function buildMonthlyClosingRows(closing: MonthlyClosing) {
  return closing.collaborators.map((collaborator) => ({
    espacio: closing.marketName,
    mes_cierre: closing.closingMonth,
    cerrado_por: closing.closedBy || "Vista previa",
    tienda: collaborator.collaboratorName,
    correo: collaborator.collaboratorEmail,
    factura: collaborator.factura ? "Si" : "No",
    ventas: collaborator.saleCount,
    items: collaborator.totalItems,
    monto_ventas: collaborator.totalSalesAmount,
    comision: collaborator.totalCommissionAmount,
    neto: collaborator.totalNetAmount,
    iva_total: collaborator.totalIvaAmount,
    iva_a_pagar: collaborator.ivaToPayAmount,
  }));
}
