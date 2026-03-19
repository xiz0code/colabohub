import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import { getCollaboratorSalesReport, getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

function today() {
  return new Date().toISOString().slice(0, 10);
}

function monthStart() {
  const date = new Date();
  return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().slice(0, 10);
}

type SalesReportTab = "today" | "collaborators";

export function SalesTodayPage({ defaultTab = "today" }: { defaultTab?: SalesReportTab }) {
  const { primaryRole } = useSession();
  const [activeTab, setActiveTab] = useState<SalesReportTab>(defaultTab);
  const [selectedCollaboratorId, setSelectedCollaboratorId] = useState("");
  const [dateFrom, setDateFrom] = useState(monthStart());
  const [dateTo, setDateTo] = useState(today());
  const salesTodayQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details"],
    queryFn: getSalesTodayDetails,
  });
  const collaboratorsQuery = useQuery({
    queryKey: ["users", "collaborators", "report-options"],
    queryFn: listUsers,
    enabled: primaryRole !== "STORE_USER",
  });
  const collaboratorReportQuery = useQuery({
    queryKey: ["reports", "sales", "collaborator", selectedCollaboratorId, dateFrom, dateTo],
    queryFn: () => getCollaboratorSalesReport(Number(selectedCollaboratorId), dateFrom, dateTo),
    enabled: selectedCollaboratorId.length > 0 && primaryRole !== "STORE_USER",
  });

  const isCollaborator = primaryRole === "STORE_USER";
  const showCollaboratorPanel = !isCollaborator && activeTab === "collaborators";
  const collaborators = useMemo(
    () => (collaboratorsQuery.data ?? []).filter((user) => user.roles.includes("STORE_USER")),
    [collaboratorsQuery.data],
  );

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mis ventas" : activeTab === "collaborators" ? "Reporte por tienda" : "Reportes de ventas"}
        description={
          isCollaborator
            ? "Vista de solo lectura con ventas confirmadas dentro de tu alcance permitido."
            : activeTab === "collaborators"
              ? "Analiza el rendimiento de cada Tienda con rango de fechas y acumulados claros."
              : "Consulta el detalle diario de ventas y profundiza en el rendimiento mensual de cada Tienda."
        }
        eyebrow={isCollaborator ? "Consulta personal" : "Operacion comercial"}
      />

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

      {salesTodayQuery.data && (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard label="Ventas confirmadas" value={String(salesTodayQuery.data.salesCount)} />
            <MetricCard label="Monto total" value={formatMoney(salesTodayQuery.data.totalAmount)} />
            <MetricCard label="Comision total" value={formatMoney(salesTodayQuery.data.totalCommission)} />
            <MetricCard label="Neto total" value={formatMoney(salesTodayQuery.data.totalNet)} />
          </div>

          {showCollaboratorPanel ? (
            <div className="soft-surface p-6">
              <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div>
                  <h2 className="text-lg font-semibold">Reporte por tienda</h2>
                  <p className="text-sm text-muted-foreground">
                    Filtra una Tienda y un rango de fechas para revisar ventas, comisiones, neto e IVA acumulado.
                  </p>
                </div>
                <span className="soft-chip">Rango personalizable</span>
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

              {collaboratorsQuery.isLoading ? <div className="mt-4"><FeedbackMessage kind="info" message="Cargando Tiendas..." /></div> : null}
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
                    <MetricCard label="Monto acumulado" value={formatMoney(collaboratorReportQuery.data.totalAmount)} />
                    <MetricCard
                      label="Comision acumulada"
                      value={formatMoney(collaboratorReportQuery.data.totalCommissionAmount)}
                    />
                    <MetricCard label="Neto acumulado" value={formatMoney(collaboratorReportQuery.data.totalNetAmount)} />
                    <MetricCard label="IVA acumulado" value={formatMoney(collaboratorReportQuery.data.totalIvaAmount)} />
                  </div>

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
                              <th>Fecha</th>
                              <th>Producto</th>
                              <th>Cantidad</th>
                              <th>Total</th>
                              <th>Comision</th>
                              <th>Neto</th>
                            </tr>
                          </thead>
                          <tbody>
                            {collaboratorReportQuery.data.entries.map((entry, index) => (
                              <tr key={`${entry.saleId}-${index}`}>
                                <td>{formatDateTime(entry.confirmedAt)}</td>
                                <td>
                                  <p className="font-medium">{entry.productName}</p>
                                  <p className="text-sm text-muted-foreground">{entry.saleNumber}</p>
                                </td>
                                <td>{entry.quantity}</td>
                                <td>{formatMoney(entry.totalAmount)}</td>
                                <td>{formatMoney(entry.commissionAmount)}</td>
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

          <div className="soft-surface p-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">{primaryRole === "ADMIN_MARKET" ? "Resumen por tiendas" : "Totales por Espacio"}</h2>
                <p className="text-sm text-muted-foreground">
                  {primaryRole === "ADMIN_MARKET"
                    ? "Vista agregada de las Tiendas con ventas visibles dentro de tu Espacio."
                    : "Vista agregada para entender rapidamente el volumen visible del dia por Espacio."}
                </p>
              </div>
              <span className="soft-chip">
                {salesTodayQuery.data.stores.length} {primaryRole === "ADMIN_MARKET" ? "tienda(s)" : "espacio(s)"}
              </span>
            </div>

            {salesTodayQuery.data.stores.length === 0 ? (
              <div className="mt-4">
                <EmptyState
                  title={isCollaborator ? "No tienes ventas visibles hoy" : "No hay ventas confirmadas hoy"}
                  description={
                    isCollaborator
                      ? "Cuando existan ventas dentro de tu alcance permitido, apareceran aqui agrupadas por Espacio."
                      : "Cuando existan ventas confirmadas apareceran aqui agrupadas por Espacio."
                  }
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
                      <th>Comision</th>
                      <th>Neto</th>
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
              {isCollaborator ? "Tu lectura diaria se mantiene sin acciones de edicion." : "Consulta cada venta con un desglose suave por Espacio."}
            </p>

            {salesTodayQuery.data.sales.length === 0 ? (
              <div className="mt-4">
                <EmptyState
                  title={isCollaborator ? "No hay ventas visibles para mostrar" : "No hay ventas para mostrar"}
                  description={
                    isCollaborator
                      ? "Cuando existan ventas confirmadas dentro de tu alcance permitido, apareceran aqui."
                      : "Confirma una venta en el POS y aparecera aqui automaticamente."
                  }
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
                        <span>Comision: {formatMoney(sale.totalCommissionAmount)}</span>
                        <span>Neto: {formatMoney(sale.totalNetAmount)}</span>
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
                              <th>Comision</th>
                              <th>Neto</th>
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
                              <th>Neto</th>
                            </tr>
                          </thead>
                          <tbody>
                            {sale.items.map((item) => (
                              <tr key={item.itemId}>
                                <td className="font-medium">{item.productName}</td>
                                <td>{item.collaboratorName ?? "Sin Tienda"}</td>
                                <td>{item.storeName}</td>
                                <td>{item.quantity}</td>
                                <td>{formatMoney(item.subtotalAmount)}</td>
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
        </div>
      )}
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

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
}

function resolveStoreSalesCount(store: {
  salesCount?: number;
  totalSales?: number;
}) {
  return toSafeNumber(store.salesCount ?? store.totalSales);
}

function resolveStoreAmount(store: {
  subtotalAmount?: number;
  totalAmount?: number;
}) {
  return toSafeNumber(store.subtotalAmount ?? store.totalAmount);
}

function resolveStoreCommission(store: {
  totalCommissionAmount?: number;
  totalCommission?: number;
}) {
  return toSafeNumber(store.totalCommissionAmount ?? store.totalCommission);
}

function resolveStoreNet(store: {
  netAmount?: number;
  totalNet?: number;
}) {
  return toSafeNumber(store.netAmount ?? store.totalNet);
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

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
