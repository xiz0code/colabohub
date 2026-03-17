import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";

import { closeDaily, getDailyClosing } from "@/features/closings/api/closingApi";
import { useSession } from "@/features/auth/session/SessionProvider";
import { listMarkets } from "@/features/markets/api/marketApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function DailyClosingPage() {
  const { roles } = useSession();
  const [selectedMarketId, setSelectedMarketId] = useState("");
  const [closingDate, setClosingDate] = useState(today());
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);

  const marketsQuery = useQuery({
    queryKey: ["markets"],
    queryFn: listMarkets,
  });

  const closingQuery = useQuery({
    queryKey: ["daily-closing", selectedMarketId, closingDate],
    queryFn: () => getDailyClosing(Number(selectedMarketId), closingDate),
    enabled: selectedMarketId.length > 0,
    retry: false,
  });

  const closingNotFound = closingQuery.error instanceof ApiError && closingQuery.error.status === 404;
  const canCloseDay = roles.some((role) => ["ADMIN_SYSTEM", "ADMIN_MARKET"].includes(role));

  const closeMutation = useMutation({
    mutationFn: () => closeDaily(Number(selectedMarketId), closingDate),
    onSuccess: () => {
      setFeedback({ kind: "success", message: "Cierre diario generado correctamente." });
      void closingQuery.refetch();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible generar el cierre diario.") });
    },
  });

  const currentClosing = closingQuery.data ?? null;

  return (
    <section>
      <PageHeader
        title="Cierres"
        description="Genera y consulta el cierre financiero diario por Tienda usando snapshots persistidos del POS."
        eyebrow="Operacion financiera"
      />

      <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
        <div className="soft-surface p-6">
          <h2 className="text-lg font-semibold">Parametros de cierre</h2>
          <p className="mt-1 text-sm text-muted-foreground">Selecciona una Tienda y una fecha para consultar o generar el cierre.</p>

          <div className="mt-4 grid gap-4">
            <label className="grid gap-2 text-sm">
              <span>Tienda</span>
              <select
                value={selectedMarketId}
                onChange={(event) => {
                  setSelectedMarketId(event.target.value);
                  setFeedback(null);
                }}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              >
                <option value="">Selecciona una Tienda</option>
                {marketsQuery.data?.map((market) => (
                  <option key={market.id} value={market.id}>
                    {market.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="grid gap-2 text-sm">
              <span>Fecha</span>
              <input
                type="date"
                value={closingDate}
                onChange={(event) => setClosingDate(event.target.value)}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>

            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                disabled={selectedMarketId.length === 0 || closeMutation.isPending || !canCloseDay}
                onClick={() => closeMutation.mutate()}
                className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
              >
                {!canCloseDay ? "Solo lectura" : closeMutation.isPending ? "Cerrando..." : "Generar cierre"}
              </button>
              <button
                type="button"
                disabled={selectedMarketId.length === 0 || closingQuery.isFetching || closeMutation.isPending}
                onClick={() => {
                  setFeedback(null);
                  void closingQuery.refetch();
                }}
                className="rounded-full border border-white/90 bg-white/75 px-4 py-3 text-sm font-semibold shadow-sm disabled:opacity-50"
              >
                Consultar cierre
              </button>
            </div>
          </div>
        </div>

        <div className="space-y-4">
          {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}
          {marketsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando Tiendas..." /> : null}
          {marketsQuery.isError ? (
            <FeedbackMessage kind="error" message={getErrorMessage(marketsQuery.error, "No fue posible cargar las Tiendas.")} />
          ) : null}

          {!selectedMarketId ? (
            <EmptyState
              title="Selecciona una Tienda"
              description="Elige una Tienda y una fecha para consultar o generar el cierre diario."
            />
          ) : closingQuery.isLoading || closingQuery.isFetching ? (
            <FeedbackMessage kind="info" message="Consultando cierre diario..." />
          ) : closingQuery.isError && closingNotFound && !currentClosing ? (
            <EmptyState
              title="No existe cierre para esa fecha"
              description="Todavia no hay cierre generado. Puedes crearlo ahora o consultar otra fecha."
            />
          ) : closingQuery.isError && !currentClosing ? (
            <FeedbackMessage
              kind="error"
              message={getErrorMessage(closingQuery.error, "No fue posible consultar el cierre diario.")}
            />
          ) : currentClosing ? (
            <>
              <div className="soft-surface p-6">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  <div>
                    <h2 className="text-lg font-semibold">{currentClosing.marketName}</h2>
                    <p className="text-sm text-muted-foreground">
                      Fecha: {currentClosing.closingDate} | Cerrado por: {currentClosing.closedBy}
                    </p>
                  </div>
                  <span className="soft-chip">{currentClosing.saleCount} venta(s)</span>
                </div>

                <div className="mt-5 grid gap-3 text-sm md:grid-cols-3">
                  <MetricCard label="Ventas" value={formatMoney(currentClosing.totalSalesAmount)} />
                  <MetricCard label="Comisiones" value={formatMoney(currentClosing.totalCommissionAmount)} />
                  <MetricCard label="Neto" value={formatMoney(currentClosing.totalNetAmount)} />
                </div>
              </div>

              <div className="soft-surface p-6">
                <h2 className="text-lg font-semibold">Desglose por tienda</h2>
                <p className="mt-1 text-sm text-muted-foreground">Resumen persistido por tienda dentro del cierre consultado.</p>

                {currentClosing.stores.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="Sin tiendas en el cierre"
                      description="No hay ventas confirmadas para esa fecha en la Tienda seleccionada."
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
                          <th>Neto</th>
                        </tr>
                      </thead>
                      <tbody>
                        {currentClosing.stores.map((store) => (
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
          ) : null}
        </div>
      </div>
    </section>
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
