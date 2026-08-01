import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, CircleDollarSign, PackageCheck, ReceiptText, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  getCollaboratorSalesReport,
  getSalesTodayDetails,
  recalculateSalesCommissions,
  type CollaboratorSalesReport,
  type SalesTodayDetails,
} from "@/features/reports/api/reportApi";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";
import { downloadCsv } from "@/shared/lib/files/downloadCsv";

function formatDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function today() {
  return formatDateInputValue(new Date());
}

function monthStart() {
  const date = new Date();
  return formatDateInputValue(new Date(date.getFullYear(), date.getMonth(), 1));
}

function monthEnd() {
  const date = new Date();
  return formatDateInputValue(new Date(date.getFullYear(), date.getMonth() + 1, 0));
}

type SalesReportTab = "today" | "collaborators";

export function SalesTodayPage({ defaultTab = "today" }: { defaultTab?: SalesReportTab }) {
  const queryClient = useQueryClient();
  const { primaryRole, user } = useSession();
  const isCollaborator = primaryRole === "STORE_USER";
  const canRecalculateCommissions =
    primaryRole === "ADMIN_SYSTEM" || primaryRole === "ADMIN_MARKET" || primaryRole === "COLLABORATOR";
  const [activeTab, setActiveTab] = useState<SalesReportTab>(defaultTab);
  const [selectedCollaboratorId, setSelectedCollaboratorId] = useState("");
  const [dateFrom, setDateFrom] = useState(monthStart());
  const [dateTo, setDateTo] = useState(today());
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);

  const reportCollaboratorId = isCollaborator ? (user?.id ?? null) : selectedCollaboratorId ? Number(selectedCollaboratorId) : null;

  const salesTodayQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details"],
    queryFn: getSalesTodayDetails,
  });

  const collaboratorsQuery = useQuery({
    queryKey: ["users", "collaborators", "report-options"],
    queryFn: listUsers,
    enabled: !isCollaborator,
  });

  const collaboratorReportQuery = useQuery({
    queryKey: ["reports", "sales", "collaborator", reportCollaboratorId, dateFrom, dateTo],
    queryFn: () => getCollaboratorSalesReport(reportCollaboratorId!, dateFrom, dateTo),
    enabled: reportCollaboratorId !== null,
  });

  const commissionRecalculationMutation = useMutation({
    mutationFn: () => recalculateSalesCommissions(dateFrom, dateTo),
    onSuccess: async (result) => {
      setFeedback({
        kind: "success",
        message: `Revisamos ${result.reviewedSales} venta(s). Actualizamos ${result.recalculatedSales} con UF distinta al dia de venta.`,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["reports", "sales"] }),
        queryClient.invalidateQueries({ queryKey: ["reports", "dashboard"] }),
      ]);
    },
    onError: (error) =>
      setFeedback({
        kind: "error",
        message: getErrorMessage(error, "No pudimos recalcular las comisiones del periodo."),
      }),
  });

  const collaborators = useMemo(
    () => (collaboratorsQuery.data ?? []).filter((listedUser) => listedUser.active && listedUser.roles.includes("STORE_USER")),
    [collaboratorsQuery.data],
  );

  const collaboratorItems = useMemo(
    () =>
      isCollaborator
        ? salesTodayQuery.data?.sales.flatMap((sale) =>
            sale.items.map((item) => ({
              ...item,
              confirmedAt: sale.confirmedAt,
              saleNumber: sale.saleNumber,
              totalAmount: sale.totalAmount,
            })),
          ) ?? []
        : [],
    [isCollaborator, salesTodayQuery.data],
  );

  const collaboratorProductSummary = useMemo(
    () =>
      Array.from(
        collaboratorItems
          .reduce((rows, item) => {
            const key = item.productName;
            const current = rows.get(key) ?? {
              productName: key,
              quantity: 0,
              totalAmount: 0,
              netAmount: 0,
            };

            current.quantity += item.quantity;
            current.totalAmount += resolveItemAmount(item);
            current.netAmount += toSafeNumber(item.netAmount);
            rows.set(key, current);
            return rows;
          }, new Map<string, { productName: string; quantity: number; totalAmount: number; netAmount: number }>())
          .values(),
      ).sort((left, right) => right.totalAmount - left.totalAmount),
    [collaboratorItems],
  );

  const dailyAverageTicket =
    salesTodayQuery.data && salesTodayQuery.data.salesCount > 0 ? salesTodayQuery.data.totalAmount / salesTodayQuery.data.salesCount : 0;
  const monthlySaleCount = collaboratorReportQuery.data ? uniqueSaleCount(collaboratorReportQuery.data.entries) : 0;
  const monthlyAverageTicket =
    collaboratorReportQuery.data && monthlySaleCount > 0 ? collaboratorReportQuery.data.totalAmount / monthlySaleCount : 0;
  const monthlyUnitsSold = collaboratorReportQuery.data?.entries.reduce((total, entry) => total + entry.quantity, 0) ?? 0;
  const monthlyClientNet =
    collaboratorReportQuery.data ? collaboratorReportQuery.data.totalAmount - collaboratorReportQuery.data.totalIvaAmount : 0;
  const monthlyBestProduct = collaboratorReportQuery.data
    ? buildMonthlyProductSummary(collaboratorReportQuery.data)[0]?.productName ?? "Sin datos"
    : "Sin datos";
  const showCollaboratorPanel = !isCollaborator && activeTab === "collaborators";

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mis ventas" : activeTab === "collaborators" ? "Reporte por tienda" : "Reportes de ventas"}
        description={
          isCollaborator
            ? "Revisa primero el movimiento de hoy y consulta después el detalle acumulado de tu Tienda."
            : activeTab === "collaborators"
              ? "Analiza el resultado de cada Tienda con rango de fechas, acumulados y detalle por producto."
              : "Consulta el movimiento diario de ventas confirmado dentro de tu alcance."
        }
        eyebrow={isCollaborator ? "Consulta personal" : "Operacion comercial"}
      />

      {feedback ? (
        <div className="mb-6">
          <FeedbackMessage kind={feedback.kind} message={feedback.message} />
        </div>
      ) : null}

      {!isCollaborator ? (
        <div className="mb-6 flex flex-wrap gap-3">
          <Link
            to="/sales/today"
            onClick={() => setActiveTab("today")}
            className={[
              "rounded-full px-4 py-2 text-sm font-semibold transition",
              activeTab === "today"
                ? "bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-white shadow-[0_10px_24px_rgba(187,156,232,0.22)]"
                : "border border-white/90 bg-white/75 text-muted-foreground shadow-sm hover:text-foreground",
            ].join(" ")}
          >
            Ventas del dia
          </Link>
          <Link
            to="/reports/collaborators"
            onClick={() => setActiveTab("collaborators")}
            className={[
              "rounded-full px-4 py-2 text-sm font-semibold transition",
              activeTab === "collaborators"
                ? "bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-white shadow-[0_10px_24px_rgba(187,156,232,0.22)]"
                : "border border-white/90 bg-white/75 text-muted-foreground shadow-sm hover:text-foreground",
            ].join(" ")}
          >
            Tiendas
          </Link>
        </div>
      ) : null}

      {salesTodayQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando ventas del dia..." /> : null}
      {salesTodayQuery.isError ? (
        <FeedbackMessage
          kind="error"
          message={getErrorMessage(salesTodayQuery.error, "No fue posible cargar las ventas del dia.")}
        />
      ) : null}

      {salesTodayQuery.data ? (
        <div className="space-y-6">
          {!isCollaborator ? <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard label="Ventas confirmadas de hoy" value={String(salesTodayQuery.data.salesCount)} />
            <MetricCard label="Monto de hoy" value={formatMoney(salesTodayQuery.data.totalAmount)} />
            <MetricCard
              label={isCollaborator ? "Ticket promedio de hoy" : "Comisión total de hoy"}
              value={formatMoney(isCollaborator ? dailyAverageTicket : salesTodayQuery.data.totalCommission)}
            />
            <MetricCard label="Neto de hoy" value={formatMoney(salesTodayQuery.data.totalNet)} />
          </div> : null}

          {isCollaborator ? (
            <div className="flex flex-col gap-6">
              <div data-testid="monthly-sales-section" className="soft-surface order-2 p-6">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Histórico y cuadratura</p>
                    <h2 className="mt-2 text-lg font-semibold">Detalle mensual de ventas</h2>
                    <p className="text-sm text-muted-foreground">
                      Consulta el acumulado del periodo, comisiones, IVA y resultado final cuando necesites revisar el mes.
                    </p>
                  </div>

                  <div className="flex flex-wrap items-end gap-3">
                    <label className="grid gap-2 text-sm">
                      <span>Desde</span>
                      <input
                        type="date"
                        value={dateFrom}
                        onChange={(event) => setDateFrom(event.target.value)}
                        className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                      />
                    </label>
                    <label className="grid gap-2 text-sm">
                      <span>Hasta</span>
                      <input
                        type="date"
                        value={dateTo}
                        onChange={(event) => setDateTo(event.target.value)}
                        className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                      />
                    </label>
                    {collaboratorReportQuery.data ? (
                      <button
                        type="button"
                        onClick={() => {
                          downloadCsv(
                            buildCollaboratorReportFilename(collaboratorReportQuery.data),
                            buildCollaboratorReportRows(collaboratorReportQuery.data),
                          );
                        }}
                        className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
                      >
                        Descargar CSV
                      </button>
                    ) : null}
                  </div>
                </div>

                {collaboratorReportQuery.isLoading || collaboratorReportQuery.isFetching ? (
                  <div className="mt-4">
                    <FeedbackMessage kind="info" message="Cargando detalle mensual..." />
                  </div>
                ) : collaboratorReportQuery.isError ? (
                  <div className="mt-4">
                    <FeedbackMessage
                      kind="error"
                      message={getErrorMessage(
                        collaboratorReportQuery.error,
                        "No fue posible cargar el detalle mensual de ventas.",
                      )}
                    />
                  </div>
                ) : collaboratorReportQuery.data ? (
                  <div className="mt-5 space-y-5">
                    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                      <MetricCard label="Ventas del periodo" value={String(monthlySaleCount)} />
                      <MetricCard label="Unidades vendidas" value={String(monthlyUnitsSold)} />
                      <MetricCard label="Ticket promedio" value={formatMoney(monthlyAverageTicket)} />
                      <MetricCard label="Producto más vendido" value={monthlyBestProduct} />
                    </div>

                    <FinancialSummaryPanel
                      totalAmount={collaboratorReportQuery.data.totalAmount}
                      clientNetAmount={monthlyClientNet}
                      clientIvaAmount={collaboratorReportQuery.data.totalIvaAmount}
                      commissionAmount={collaboratorReportQuery.data.totalCommissionAmount}
                      payoutAmount={collaboratorReportQuery.data.totalNetAmount}
                    />

                    {collaboratorReportQuery.data.entries.length === 0 ? (
                      <EmptyState
                        title="Todavia no tienes ventas en este periodo"
                        description="Ajusta el rango de fechas para revisar otro tramo del mes."
                      />
                    ) : (
                      <div className="soft-table">
                        <table>
                          <thead>
                            <tr>
                              <th>Fecha y hora</th>
                              <th>Venta</th>
                              <th>Producto</th>
                              <th>Cantidad</th>
                              <th>Precio</th>
                              <th>Promoción</th>
                              <th>Total cliente</th>
                              <th>Neto cliente</th>
                              <th>IVA cliente</th>
                              <th>UF</th>
                              <th>Comisión fija</th>
                              <th>Comisión variable</th>
                              <th>IVA comisión</th>
                              <th>Total a recibir</th>
                            </tr>
                          </thead>
                          <tbody>
                            {collaboratorReportQuery.data.entries.map((entry, index) => (
                              <tr key={`${entry.saleId}-${index}`}>
                                <td>
                                  <DateTimeCell value={entry.confirmedAt} />
                                </td>
                                <td>
                                  <p className="font-medium">{entry.saleNumber}</p>
                                  <p className="text-xs text-muted-foreground">{translatePaymentMethod(entry.paymentMethod)}</p>
                                </td>
                                <td className="font-medium">{entry.productName}</td>
                                <td>{entry.quantity}</td>
                                <td>{formatMoney(entry.unitPrice)}</td>
                                <td>{entry.promotionLabel}</td>
                                <td>{formatMoney(entry.totalAmount)}</td>
                                <td>{formatMoney(resolveClientNet(entry.totalAmount))}</td>
                                <td>{formatMoney(resolveClientIva(entry.totalAmount))}</td>
                                <td>{entry.ufValue ? formatDecimal(entry.ufValue) : "-"}</td>
                                <td>{formatMoney(entry.fixedCommissionAmount)}</td>
                                <td>{formatMoney(entry.variableCommissionAmount)}</td>
                                <td>{formatMoney(entry.commissionIvaAmount)}</td>
                                <td>{formatMoney(entry.netAmount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                ) : null}
              </div>

              <section
                data-testid="daily-sales-section"
                className="order-1 border-y border-violet-100/80 bg-white/55 px-4 py-6 shadow-[0_18px_52px_rgba(126,94,173,0.10)] sm:px-6"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                  <div>
                    <div className="flex items-center gap-2 text-violet-700">
                      <CalendarDays className="h-4 w-4" aria-hidden="true" />
                      <p className="text-xs font-semibold uppercase tracking-[0.18em]">Tu jornada de hoy</p>
                    </div>
                    <h2 className="mt-2 text-2xl font-bold text-foreground">Así se están moviendo tus ventas</h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Revisa primero lo que pasó hoy y detecta rápidamente tus ventas y productos con mayor movimiento.
                    </p>
                  </div>
                  <span className="inline-flex w-fit items-center rounded-full border border-violet-100 bg-violet-50 px-3 py-1.5 text-sm font-semibold text-violet-800">
                    {formatBusinessDate(salesTodayQuery.data.businessDate)}
                  </span>
                </div>

                <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  <DailyMetric
                    icon={ReceiptText}
                    label="Ventas confirmadas"
                    value={String(salesTodayQuery.data.salesCount)}
                    tone="violet"
                  />
                  <DailyMetric
                    icon={CircleDollarSign}
                    label="Total vendido"
                    value={formatMoney(salesTodayQuery.data.totalAmount)}
                    tone="emerald"
                  />
                  <DailyMetric
                    icon={PackageCheck}
                    label="Ticket promedio"
                    value={formatMoney(dailyAverageTicket)}
                    tone="sky"
                  />
                  <DailyMetric
                    icon={CircleDollarSign}
                    label="Total a recibir"
                    value={formatMoney(salesTodayQuery.data.totalNet)}
                    tone="amber"
                  />
                </div>

                <div className="mt-6 grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
                <div className="min-w-0 border-t border-violet-100/80 pt-5 xl:border-r xl:border-t-0 xl:pr-6">
                  <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                    <div>
                      <h2 className="text-lg font-semibold">Productos con mejor movimiento hoy</h2>
                      <p className="text-sm text-muted-foreground">
                        Mira que productos se movieron mas rapido y cuanto dejaron en la jornada actual.
                      </p>
                    </div>
                    <span className="soft-chip">{collaboratorProductSummary.length} producto(s)</span>
                  </div>

                  {collaboratorProductSummary.length === 0 ? (
                    <div className="mt-4">
                      <EmptyState
                        title="Sin productos vendidos hoy"
                        description="Cuando existan ventas confirmadas dentro de tu alcance, aqui veras el ranking de productos."
                      />
                    </div>
                  ) : (
                    <div className="mt-5 space-y-3">
                      {collaboratorProductSummary.slice(0, 6).map((row) => (
                        <SalesBar
                          key={row.productName}
                          label={row.productName}
                          amount={row.totalAmount}
                          maxAmount={Math.max(...collaboratorProductSummary.map((item) => item.totalAmount), 1)}
                        />
                      ))}

                      <div className="soft-table mt-4">
                        <table>
                          <thead>
                            <tr>
                              <th>Producto</th>
                              <th>Unidades</th>
                              <th>Monto</th>
                              <th>Total a recibir</th>
                            </tr>
                          </thead>
                          <tbody>
                            {collaboratorProductSummary.map((row) => (
                              <tr key={row.productName}>
                                <td className="font-medium">{row.productName}</td>
                                <td>{row.quantity}</td>
                                <td>{formatMoney(row.totalAmount)}</td>
                                <td>{formatMoney(row.netAmount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </div>

                <div className="min-w-0 border-t border-violet-100/80 pt-5 xl:border-t-0 xl:pl-1">
                  <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                    <div>
                      <h2 className="text-lg font-semibold">Ultimas ventas confirmadas</h2>
                      <p className="text-sm text-muted-foreground">
                        Revisa las ventas mas recientes del dia con hora, monto total y productos incluidos.
                      </p>
                    </div>
                    <span className="soft-chip">{salesTodayQuery.data.sales.length} venta(s)</span>
                  </div>

                  {salesTodayQuery.data.sales.length === 0 ? (
                    <div className="mt-4">
                      <EmptyState
                        title="No tienes ventas visibles hoy"
                        description="Apenas haya ventas confirmadas propias, apareceran aqui con su detalle."
                      />
                    </div>
                  ) : (
                    <div className="mt-5 space-y-3">
                      {salesTodayQuery.data.sales.map((sale) => (
                        <article key={sale.saleId} className="rounded-lg border border-white/85 bg-white/80 px-4 py-4 shadow-sm">
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-semibold text-foreground">{sale.saleNumber}</p>
                              <p className="mt-1 text-sm text-muted-foreground">{formatDateTime(sale.confirmedAt)}</p>
                            </div>
                            <span className="soft-chip">{formatMoney(sale.totalAmount)}</span>
                          </div>
                          <div className="mt-4 flex flex-wrap gap-2">
                            {sale.items.map((item) => (
                              <span key={item.itemId} className="rounded-full bg-secondary/70 px-3 py-1 text-xs font-semibold text-muted-foreground">
                                {item.quantity} x {item.productName}
                              </span>
                            ))}
                          </div>
                        </article>
                      ))}
                    </div>
                  )}
                </div>
                </div>
              </section>
            </div>
          ) : null}

          {showCollaboratorPanel ? (
            <div className="soft-surface p-6">
              <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div>
                  <h2 className="text-lg font-semibold">Reporte por tienda</h2>
                  <p className="text-sm text-muted-foreground">
                    Filtra una Tienda y un rango de fechas para revisar ventas, comisiones, IVA y total a recibir.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className="soft-chip">Rango personalizable</span>
                  {canRecalculateCommissions ? (
                    <button
                      type="button"
                      onClick={() => commissionRecalculationMutation.mutate()}
                      disabled={commissionRecalculationMutation.isPending}
                      className="rounded-full border border-amber-100 bg-amber-50/90 px-4 py-2 text-sm font-semibold text-amber-800 shadow-sm transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {commissionRecalculationMutation.isPending ? "Recalculando..." : "Recalcular comisiones"}
                    </button>
                  ) : null}
                  {collaboratorReportQuery.data ? (
                    <button
                      type="button"
                      onClick={() => {
                        downloadCsv(
                          buildCollaboratorReportFilename(collaboratorReportQuery.data),
                          buildCollaboratorReportRows(collaboratorReportQuery.data),
                        );
                      }}
                      className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
                    >
                      Descargar CSV
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="mt-4 grid gap-4 lg:grid-cols-[1.4fr_repeat(2,minmax(0,1fr))]">
                <label className="grid gap-2 text-sm">
                  <span>Tienda</span>
                  <select
                    value={selectedCollaboratorId}
                    onChange={(event) => setSelectedCollaboratorId(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  >
                    <option value="">Selecciona una Tienda</option>
                    {collaborators.map((collaborator) => (
                      <option key={collaborator.id} value={collaborator.id}>
                        {collaborator.fullName}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Desde</span>
                  <input
                    type="date"
                    value={dateFrom}
                    onChange={(event) => setDateFrom(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Hasta</span>
                  <input
                    type="date"
                    value={dateTo}
                    onChange={(event) => setDateTo(event.target.value)}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => {
                    const currentDay = today();
                    setDateFrom(currentDay);
                    setDateTo(currentDay);
                  }}
                  className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold text-muted-foreground shadow-sm transition hover:-translate-y-0.5 hover:text-foreground"
                >
                  Ver dia actual
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setDateFrom(monthStart());
                    setDateTo(monthEnd());
                  }}
                  className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold text-muted-foreground shadow-sm transition hover:-translate-y-0.5 hover:text-foreground"
                >
                  Ver mes actual
                </button>
                <span className="rounded-full bg-secondary/60 px-4 py-2 text-sm font-medium text-muted-foreground">
                  Para otro periodo ajusta Desde y Hasta.
                </span>
              </div>

              {collaboratorsQuery.isLoading ? (
                <div className="mt-4">
                  <FeedbackMessage kind="info" message="Cargando Tiendas..." />
                </div>
              ) : null}
              {collaboratorsQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage
                    kind="error"
                    message={getErrorMessage(collaboratorsQuery.error, "No fue posible cargar las Tiendas.")}
                  />
                </div>
              ) : null}

              {selectedCollaboratorId.length === 0 ? (
                <div className="mt-4">
                  <EmptyState
                    title="Selecciona una Tienda"
                    description="Cuando elijas una Tienda y un rango de fechas, veras su resumen consolidado y el detalle de ventas."
                  />
                </div>
              ) : collaboratorReportQuery.isLoading || collaboratorReportQuery.isFetching ? (
                <div className="mt-4">
                  <FeedbackMessage kind="info" message="Consultando reporte por tienda..." />
                </div>
              ) : collaboratorReportQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage
                    kind="error"
                    message={getErrorMessage(collaboratorReportQuery.error, "No fue posible cargar el reporte de la Tienda.")}
                  />
                </div>
              ) : collaboratorReportQuery.data ? (
                <div className="mt-5 space-y-5">
                  <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <MetricCard label="Ventas del periodo" value={String(monthlySaleCount)} />
                    <MetricCard label="Unidades vendidas" value={String(monthlyUnitsSold)} />
                    <MetricCard label="Ticket promedio" value={formatMoney(monthlyAverageTicket)} />
                    <MetricCard label="Producto más vendido" value={monthlyBestProduct} />
                  </div>

                  <FinancialSummaryPanel
                    totalAmount={collaboratorReportQuery.data.totalAmount}
                    clientNetAmount={monthlyClientNet}
                    clientIvaAmount={collaboratorReportQuery.data.totalIvaAmount}
                    commissionAmount={collaboratorReportQuery.data.totalCommissionAmount}
                    payoutAmount={collaboratorReportQuery.data.totalNetAmount}
                  />

                  <div className="rounded-[24px] border border-white/85 bg-white/65 p-5 shadow-sm">
                    <h3 className="text-base font-semibold">{collaboratorReportQuery.data.collaboratorName}</h3>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Periodo consultado: {collaboratorReportQuery.data.dateFrom} al {collaboratorReportQuery.data.dateTo}
                    </p>

                    {collaboratorReportQuery.data.entries.length === 0 ? (
                      <div className="mt-4">
                        <EmptyState
                          title="Sin ventas en el periodo"
                          description="Ajusta el rango o selecciona otra Tienda para revisar resultados."
                        />
                      </div>
                    ) : (
                      <div className="soft-table mt-4">
                        <table>
                          <thead>
                            <tr>
                              <th>Fecha y hora</th>
                              <th>Venta</th>
                              <th>Producto</th>
                              <th>Cantidad</th>
                              <th>Precio</th>
                              <th>Promoción</th>
                              <th>Total cliente</th>
                              <th>Neto cliente</th>
                              <th>IVA cliente</th>
                              <th>UF</th>
                              <th>Comisión fija</th>
                              <th>Comisión variable</th>
                              <th>IVA comisión</th>
                              <th>Total a recibir</th>
                            </tr>
                          </thead>
                          <tbody>
                            {collaboratorReportQuery.data.entries.map((entry, index) => (
                              <tr key={`${entry.saleId}-${index}`}>
                                <td>
                                  <DateTimeCell value={entry.confirmedAt} />
                                </td>
                                <td>
                                  <p className="font-medium">{entry.saleNumber}</p>
                                  <p className="text-xs text-muted-foreground">{translatePaymentMethod(entry.paymentMethod)}</p>
                                </td>
                                <td className="font-medium">{entry.productName}</td>
                                <td>{entry.quantity}</td>
                                <td>{formatMoney(entry.unitPrice)}</td>
                                <td>{entry.promotionLabel}</td>
                                <td>{formatMoney(entry.totalAmount)}</td>
                                <td>{formatMoney(resolveClientNet(entry.totalAmount))}</td>
                                <td>{formatMoney(resolveClientIva(entry.totalAmount))}</td>
                                <td>{entry.ufValue ? formatDecimal(entry.ufValue) : "-"}</td>
                                <td>{formatMoney(entry.fixedCommissionAmount)}</td>
                                <td>{formatMoney(entry.variableCommissionAmount)}</td>
                                <td>{formatMoney(entry.commissionIvaAmount)}</td>
                                <td>{formatMoney(entry.netAmount)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          {!isCollaborator ? (
            <>
              <div className="soft-surface p-6">
                <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                  <div>
                    <h2 className="text-lg font-semibold">
                      {primaryRole === "ADMIN_MARKET" ? "Resumen por tiendas" : "Totales por Espacio"}
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      {primaryRole === "ADMIN_MARKET"
                        ? "Vista agregada de las Tiendas con ventas visibles dentro de tu Espacio."
                        : "Vista agregada para entender rapidamente el volumen visible del dia por Espacio."}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <span className="soft-chip">
                      {salesTodayQuery.data.stores.length} {primaryRole === "ADMIN_MARKET" ? "tienda(s)" : "espacio(s)"}
                    </span>
                    {salesTodayQuery.data.stores.length > 0 ? (
                      <button
                        type="button"
                        onClick={() => {
                          downloadCsv(buildDailySummaryFilename(salesTodayQuery.data.businessDate), buildDailySummaryRows(salesTodayQuery.data));
                        }}
                        className="rounded-full border border-white/90 bg-white/80 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
                      >
                        Descargar CSV
                      </button>
                    ) : null}
                  </div>
                </div>

                {salesTodayQuery.data.stores.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="No hay ventas confirmadas hoy"
                      description="Cuando existan ventas confirmadas apareceran aqui agrupadas por Espacio."
                    />
                  </div>
                ) : (
                  <div className="soft-table mt-4">
                    <table>
                      <thead>
                        <tr>
                          <th>Espacio</th>
                          <th>Ventas</th>
                          <th>Monto</th>
                          <th>Comisión</th>
                          <th>Total a recibir</th>
                        </tr>
                      </thead>
                      <tbody>
                        {salesTodayQuery.data.stores.map((store) => (
                          <tr key={store.storeId}>
                            <td className="font-medium">{store.storeName}</td>
                            <td>{resolveStoreSalesCount(store)}</td>
                            <td>{formatMoney(resolveStoreAmount(store))}</td>
                            <td>{formatMoney(resolveStoreCommission(store))}</td>
                            <td>{formatMoney(resolveStoreNet(store))}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="soft-surface p-6">
                <h2 className="text-lg font-semibold">Detalle de ventas confirmadas</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Consulta cada venta del dia con un desglose legible por Espacio y por producto.
                </p>

                {salesTodayQuery.data.sales.length === 0 ? (
                  <div className="mt-4">
                    <EmptyState
                      title="No hay ventas para mostrar"
                      description="Confirma una venta en el POS y aparecera aqui automaticamente."
                    />
                  </div>
                ) : (
                  <div className="mt-4 space-y-4">
                    {salesTodayQuery.data.sales.map((sale) => (
                      <article key={sale.saleId} className="soft-subtle-surface p-4">
                        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                          <div>
                            <p className="text-sm text-muted-foreground">Venta</p>
                            <h3 className="text-base font-semibold">{sale.saleNumber}</h3>
                            <p className="text-sm text-muted-foreground">Confirmada: {formatDateTime(sale.confirmedAt)}</p>
                          </div>
                          <div className="grid gap-1 text-sm md:text-right">
                            <span>Total: {formatMoney(sale.totalAmount)}</span>
                            <span>Comisión: {formatMoney(sale.totalCommissionAmount)}</span>
                            <span>Total a recibir: {formatMoney(sale.totalNetAmount)}</span>
                          </div>
                        </div>

                        {sale.stores.length > 0 ? (
                          <div className="soft-table mt-4">
                            <table>
                              <thead>
                                <tr>
                                  <th>Espacio</th>
                                  <th>Lineas</th>
                                  <th>Unidades</th>
                                  <th>Subtotal</th>
                                  <th>Comisión</th>
                                  <th>Total a recibir</th>
                                </tr>
                              </thead>
                              <tbody>
                                {sale.stores.map((store) => (
                                  <tr key={`${sale.saleId}-${store.storeId}`}>
                                    <td className="font-medium">{store.storeName}</td>
                                    <td>{store.lineCount}</td>
                                    <td>{store.unitCount}</td>
                                    <td>{formatMoney(store.subtotalAmount)}</td>
                                    <td>{formatMoney(store.totalCommissionAmount)}</td>
                                    <td>{formatMoney(store.netAmount)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        ) : null}

                        {sale.items.length > 0 ? (
                          <div className="soft-table mt-4">
                            <table>
                              <thead>
                                <tr>
                                  <th>Producto</th>
                                  <th>Tienda</th>
                                  <th>Espacio</th>
                                  <th>Cant.</th>
                                  <th>Subtotal</th>
                                  <th>Total a recibir</th>
                                </tr>
                              </thead>
                              <tbody>
                                {sale.items.map((item) => (
                                  <tr key={item.itemId}>
                                    <td className="font-medium">{item.productName}</td>
                                    <td>{item.collaboratorName ?? "Sin Tienda"}</td>
                                    <td>{item.storeName}</td>
                                    <td>{item.quantity}</td>
                                    <td>{formatMoney(resolveItemAmount(item))}</td>
                                    <td>{formatMoney(item.netAmount)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        ) : null}
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="soft-surface p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-3 text-3xl font-semibold tracking-tight">{value}</p>
    </div>
  );
}

function DailyMetric({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  tone: "violet" | "emerald" | "sky" | "amber";
}) {
  const toneClasses = {
    violet: "border-violet-100 bg-violet-50/75 text-violet-900",
    emerald: "border-emerald-100 bg-emerald-50/75 text-emerald-900",
    sky: "border-sky-100 bg-sky-50/75 text-sky-900",
    amber: "border-amber-100 bg-amber-50/75 text-amber-900",
  }[tone];

  return (
    <div className={["min-w-0 rounded-lg border p-4 shadow-sm", toneClasses].join(" ")}>
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] opacity-75">
        <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span>{label}</span>
      </div>
      <p className="mt-3 break-words text-2xl font-bold">{value}</p>
    </div>
  );
}

function FinancialSummaryPanel({
  totalAmount,
  clientNetAmount,
  clientIvaAmount,
  commissionAmount,
  payoutAmount,
}: {
  totalAmount: number;
  clientNetAmount: number;
  clientIvaAmount: number;
  commissionAmount: number;
  payoutAmount: number;
}) {
  return (
    <div className="rounded-[30px] border border-white/85 bg-[linear-gradient(135deg,rgba(255,255,255,0.96),rgba(255,247,252,0.92))] p-5 shadow-[0_18px_42px_rgba(186,170,211,0.12)]">
      <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.22em] text-violet-500">Resumen financiero</p>
          <h3 className="mt-2 text-lg font-semibold text-foreground">Cuánto vendió, cuánto se descuenta y cuánto recibe</h3>
        </div>
        <p className="max-w-xl text-sm text-muted-foreground">
          El <span className="font-semibold text-foreground">Total a recibir</span> corresponde al total vendido menos las comisiones del Espacio.
        </p>
      </div>

      <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <SummaryValue label="Total cobrado al cliente" value={formatMoney(totalAmount)} tone="client" />
        <SummaryValue label="Neto cliente sin IVA" value={formatMoney(clientNetAmount)} tone="neutral" />
        <SummaryValue label="IVA pagado por cliente" value={formatMoney(clientIvaAmount)} tone="iva" />
        <SummaryValue label="Comisiones del Espacio" value={formatMoney(commissionAmount)} tone="commission" />
        <SummaryValue label="Total a recibir" value={formatMoney(payoutAmount)} tone="payout" />
      </div>
    </div>
  );
}

function SummaryValue({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: "client" | "neutral" | "iva" | "commission" | "payout";
}) {
  const toneClasses = {
    client: "bg-violet-50/80 text-violet-900",
    neutral: "bg-slate-50/90 text-slate-900",
    iva: "bg-sky-50/85 text-sky-900",
    commission: "bg-amber-50/85 text-amber-900",
    payout: "bg-emerald-50/90 text-emerald-900 ring-1 ring-emerald-100",
  }[tone];

  return (
    <div className={["rounded-[22px] border border-white/85 p-4 shadow-sm", toneClasses].join(" ")}>
      <p className="text-xs font-semibold uppercase tracking-[0.14em] opacity-70">{label}</p>
      <p className="mt-3 text-2xl font-black tracking-tight">{value}</p>
    </div>
  );
}

function SalesBar({ label, amount, maxAmount }: { label: string; amount: number; maxAmount: number }) {
  const width = maxAmount > 0 ? Math.max((amount / maxAmount) * 100, 8) : 0;

  return (
    <div className="grid gap-2 md:grid-cols-[160px_1fr_120px] md:items-center">
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

function DateTimeCell({ value }: { value: string }) {
  const date = new Date(value);
  return (
    <div className="min-w-[92px] leading-tight">
      <p className="whitespace-nowrap text-sm font-medium text-foreground">
        {new Intl.DateTimeFormat("es-CL", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date)}
      </p>
      <p className="mt-1 whitespace-nowrap text-xs text-muted-foreground">
        {new Intl.DateTimeFormat("es-CL", { hour: "2-digit", minute: "2-digit" }).format(date)}
      </p>
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

function formatBusinessDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  const date = year && month && day ? new Date(year, month - 1, day) : new Date(value);
  return new Intl.DateTimeFormat("es-CL", {
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(date);
}

function formatDecimal(value: number) {
  return new Intl.NumberFormat("es-CL", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function resolveStoreSalesCount(store: { salesCount?: number; totalSales?: number }) {
  return toSafeNumber(store.salesCount ?? store.totalSales);
}

function resolveStoreAmount(store: { subtotalAmount?: number; totalAmount?: number }) {
  return toSafeNumber(store.subtotalAmount ?? store.totalAmount);
}

function resolveStoreCommission(store: { totalCommissionAmount?: number; totalCommission?: number }) {
  return toSafeNumber(store.totalCommissionAmount ?? store.totalCommission);
}

function resolveStoreNet(store: { netAmount?: number; totalNet?: number }) {
  return toSafeNumber(store.netAmount ?? store.totalNet);
}

function resolveItemAmount(item: {
  subtotalAmount?: number;
  subtotal?: number;
  totalCommissionAmount?: number;
  netAmount?: number;
}) {
  const directSubtotal = toSafeNumber(item.subtotalAmount ?? item.subtotal);
  if (directSubtotal > 0) {
    return directSubtotal;
  }

  return toSafeNumber(item.netAmount) + toSafeNumber(item.totalCommissionAmount);
}

function resolveClientIva(totalAmount: number) {
  const safeAmount = toSafeNumber(totalAmount);
  return safeAmount - resolveClientNet(safeAmount);
}

function resolveClientNet(totalAmount: number) {
  const safeAmount = toSafeNumber(totalAmount);
  return safeAmount / 1.19;
}

function toSafeNumber(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function uniqueSaleCount(entries: Array<{ saleId: number }>) {
  return new Set(entries.map((entry) => entry.saleId)).size;
}

function translatePaymentMethod(paymentMethod: string | null) {
  if (!paymentMethod) {
    return "Medio no informado";
  }

  switch (paymentMethod) {
    case "CASH":
      return "Efectivo";
    case "DEBIT":
    case "DEBITO":
      return "Debito";
    case "CREDIT":
      return "Credito";
    default:
      return paymentMethod;
  }
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

function buildDailySummaryFilename(date: string) {
  return `ventas-dia-${date}.csv`;
}

function buildCollaboratorReportFilename(report: CollaboratorSalesReport) {
  return `ventas-tienda-${slugify(report.collaboratorName)}-${report.dateFrom}-a-${report.dateTo}.csv`;
}

function buildDailySummaryRows(data: SalesTodayDetails) {
  return data.stores.map((store) => ({
    fecha: data.businessDate,
    espacio: store.storeName,
    ventas: resolveStoreSalesCount(store),
    monto: resolveStoreAmount(store),
    comision: resolveStoreCommission(store),
    neto: resolveStoreNet(store),
  }));
}

function buildCollaboratorReportRows(report: CollaboratorSalesReport) {
  return report.entries.map((entry) => ({
    tienda: report.collaboratorName,
    fecha_desde: report.dateFrom,
    fecha_hasta: report.dateTo,
    fecha_venta: entry.confirmedAt,
    numero_venta: entry.saleNumber,
    metodo_pago: translatePaymentMethod(entry.paymentMethod),
    producto: entry.productName,
    cantidad: entry.quantity,
    precio: entry.unitPrice,
    promocion: entry.promotionLabel,
    total_cliente: entry.totalAmount,
    neto_cliente_sin_iva: resolveClientNet(entry.totalAmount),
    iva_cliente: resolveClientIva(entry.totalAmount),
    uf: entry.ufValue,
    comision_fija: entry.fixedCommissionAmount,
    comision_variable: entry.variableCommissionAmount,
    iva_comision: entry.commissionIvaAmount,
    comision_total: entry.commissionAmount,
    total_a_recibir: entry.netAmount,
  }));
}

function buildMonthlyProductSummary(report: CollaboratorSalesReport) {
  return Array.from(
    report.entries
      .reduce((rows, entry) => {
        const current = rows.get(entry.productName) ?? {
          productName: entry.productName,
          quantity: 0,
          totalAmount: 0,
          netAmount: 0,
        };

        current.quantity += entry.quantity;
        current.totalAmount += toSafeNumber(entry.totalAmount);
        current.netAmount += toSafeNumber(entry.netAmount);
        rows.set(entry.productName, current);
        return rows;
      }, new Map<string, { productName: string; quantity: number; totalAmount: number; netAmount: number }>())
      .values(),
  ).sort((left, right) => right.quantity - left.quantity || right.totalAmount - left.totalAmount);
}
