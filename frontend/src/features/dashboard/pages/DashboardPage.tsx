import { useQuery } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { getDashboardSummary, getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { StatCard } from "@/shared/components/ui/StatCard";
import { ApiError } from "@/shared/lib/api/client";

export function DashboardPage() {
  const { primaryRole } = useSession();
  const isCollaborator = primaryRole === "STORE_USER";
  const isMarketAdmin = primaryRole === "ADMIN_MARKET";
  const dashboardQuery = useQuery({
    queryKey: ["reports", "dashboard"],
    queryFn: getDashboardSummary,
  });
  const salesDetailsQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details", "dashboard"],
    queryFn: getSalesTodayDetails,
    enabled: isMarketAdmin,
  });

  const stats = dashboardQuery.data
    ? [
        {
          label: "Ventas del dia",
          value: formatMoney(dashboardQuery.data.totalAmount),
          helper: `${dashboardQuery.data.salesCount} venta(s) visibles hoy.`,
        },
        {
          label: "Comisiones del dia",
          value: formatMoney(dashboardQuery.data.totalCommission),
          helper: "Calculadas solo sobre el alcance permitido por tu rol.",
        },
        {
          label: "Productos activos",
          value: String(dashboardQuery.data.activeProducts),
          helper: "Productos visibles actualmente para tu alcance.",
        },
        {
          label: "Stock bajo",
          value: String(dashboardQuery.data.lowStockProducts),
          helper: "Productos activos con stock igual o menor a 5.",
        },
      ]
    : [];

  const collaboratorRows = isMarketAdmin && salesDetailsQuery.data
    ? Array.from(
        salesDetailsQuery.data.sales
          .flatMap((sale) => sale.items)
          .reduce((rows, item) => {
            const key = item.collaboratorName?.trim() || "Sin Tienda";
            const current = rows.get(key) ?? {
              collaboratorName: key,
              totalSales: 0,
              totalAmount: 0,
              totalCommission: 0,
              totalNet: 0,
            };

            current.totalSales += item.quantity;
            current.totalAmount += toSafeNumber(item.subtotalAmount);
            current.totalCommission += toSafeNumber(item.totalCommissionAmount);
            current.totalNet += toSafeNumber(item.netAmount);
            rows.set(key, current);
            return rows;
          }, new Map<string, {
            collaboratorName: string;
            totalSales: number;
            totalAmount: number;
            totalCommission: number;
            totalNet: number;
          }>())
          .values(),
      )
    : [];

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mi dashboard" : "Dashboard"}
        description={
          isCollaborator
            ? "Resumen de solo lectura con ventas, productos visibles y alertas rapidas dentro de tu alcance permitido."
            : "Resumen ejecutivo con ventas confirmadas, productos visibles y actividad operativa del dia."
        }
        eyebrow={isCollaborator ? "Vista consultiva" : "Resumen general"}
      />

      {dashboardQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando resumen del dashboard..." /> : null}
      {dashboardQuery.isError ? (
        <FeedbackMessage
          kind="error"
          message={getErrorMessage(dashboardQuery.error, "No fue posible cargar el resumen del dashboard.")}
        />
      ) : null}

      {dashboardQuery.data ? (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => (
              <StatCard key={stat.label} {...stat} />
            ))}
          </div>

          <div className="soft-surface p-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">
                  {isCollaborator ? "Tiendas visibles hoy" : isMarketAdmin ? "Resumen por tienda" : "Totales por Espacio"}
                </h2>
                <p className="text-sm text-muted-foreground">
                  {isMarketAdmin
                    ? "Desglose operativo de ventas del dia por Tienda dentro de tu Espacio."
                    : "Desglose suave y legible para revisar el movimiento visible del dia por Espacio."}
                </p>
              </div>
              <span className="soft-chip">
                {isMarketAdmin
                  ? `${collaboratorRows.length} tienda(s) con ventas`
                  : `${dashboardQuery.data.stores.length} espacio(s) con actividad`}
              </span>
            </div>

            {(isMarketAdmin ? collaboratorRows.length === 0 : dashboardQuery.data.stores.length === 0) ? (
              <div className="mt-4">
                <EmptyState
                  title={isCollaborator ? "Sin ventas visibles hoy" : isMarketAdmin ? "Sin ventas por tienda hoy" : "Sin ventas confirmadas hoy"}
                  description={
                    isCollaborator
                      ? "Cuando existan ventas dentro de tu alcance, el dashboard mostrara el desglose por Espacio."
                      : isMarketAdmin
                        ? "Cuando existan ventas confirmadas en tu Espacio, aqui veras el resumen por Tienda."
                      : "Cuando existan ventas confirmadas, el dashboard mostrara el desglose por Espacio."
                  }
                />
              </div>
            ) : (
              <div className="soft-table mt-4">
                <table>
                  <thead>
                    <tr>
                      <th>{isMarketAdmin ? "Tienda" : "Espacio"}</th>
                      <th>{isMarketAdmin ? "Productos vendidos" : "Ventas"}</th>
                      <th>Monto</th>
                      <th>Comision</th>
                      <th>Neto</th>
                    </tr>
                  </thead>
                  <tbody>
                    {isMarketAdmin
                      ? collaboratorRows.map((row) => (
                          <tr key={row.collaboratorName}>
                            <td className="font-medium">{row.collaboratorName}</td>
                            <td>{row.totalSales}</td>
                            <td>{formatMoney(row.totalAmount)}</td>
                            <td>{formatMoney(row.totalCommission)}</td>
                            <td>{formatMoney(row.totalNet)}</td>
                          </tr>
                        ))
                      : dashboardQuery.data.stores.map((store) => (
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
        </div>
      ) : null}
    </section>
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

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
