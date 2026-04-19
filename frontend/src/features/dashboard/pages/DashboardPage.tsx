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
  const shouldLoadOperationalDetails = isMarketAdmin || isCollaborator;
  const dashboardQuery = useQuery({
    queryKey: ["reports", "dashboard"],
    queryFn: getDashboardSummary,
  });
  const salesDetailsQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details", "dashboard"],
    queryFn: getSalesTodayDetails,
    enabled: shouldLoadOperationalDetails,
  });

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
            current.totalAmount += resolveItemAmount(item);
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

  const collaboratorProductRows = isCollaborator && salesDetailsQuery.data
    ? Array.from(
        salesDetailsQuery.data.sales
          .flatMap((sale) => sale.items)
          .reduce((rows, item) => {
            const key = item.productName.trim();
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
          }, new Map<string, {
            productName: string;
            quantity: number;
            totalAmount: number;
            netAmount: number;
          }>())
          .values(),
      ).sort((left, right) => right.quantity - left.quantity)
    : [];

  const salesTrend = shouldLoadOperationalDetails && salesDetailsQuery.data
    ? salesDetailsQuery.data.sales
        .map((sale) => ({
          label: sale.saleNumber.replace(/^S-/, ""),
          timeLabel: formatTime(sale.confirmedAt),
          amount: toSafeNumber(sale.totalAmount),
        }))
        .filter((sale) => sale.amount > 0)
    : [];

  const cumulativeTrend = salesTrend.reduce<Array<{ label: string; amount: number }>>((points, sale) => {
    const previousAmount = points.length > 0 ? points[points.length - 1].amount : 0;
    points.push({
      label: sale.timeLabel,
      amount: previousAmount + sale.amount,
    });
    return points;
  }, []);

  const comparisonBars = isMarketAdmin
    ? collaboratorRows.map((row) => ({
        label: row.collaboratorName,
        amount: row.totalAmount,
      }))
    : isCollaborator
      ? collaboratorProductRows.map((row) => ({
          label: row.productName,
          amount: row.totalAmount,
        }))
      : (dashboardQuery.data?.stores ?? []).map((store) => ({
          label: store.storeName,
          amount: resolveStoreAmount(store),
        }));

  const stats = dashboardQuery.data
    ? buildDashboardStats({
        dashboard: dashboardQuery.data,
        details: salesDetailsQuery.data,
        isCollaborator,
        isMarketAdmin,
        collaboratorRows,
        collaboratorProductRows,
      })
    : [];

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mi dashboard" : "Dashboard"}
        description={
          isCollaborator
            ? "Resumen comercial del dia con foco en ventas, productos mas movidos y lectura clara de tu desempeno."
            : isMarketAdmin
              ? "Vista operativa del Espacio con ventas, ticket promedio y rendimiento por Tienda en tiempo real."
              : "Resumen ejecutivo del dia con foco en ventas, ticket promedio y actividad por Espacio."
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

          {salesTrend.length > 0 ? (
            <div className="soft-surface p-6">
              <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
                <div>
                  <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                    <div>
                      <h2 className="text-lg font-semibold">
                        {isCollaborator ? "Acumulado del dia" : "Linea de ventas del dia"}
                      </h2>
                      <p className="text-sm text-muted-foreground">
                        {isCollaborator
                          ? "Sigue como va creciendo tu venta acumulada durante la jornada."
                          : "Visualiza la evolucion acumulada de ventas confirmadas durante el dia."}
                      </p>
                    </div>
                    <span className="soft-chip">{salesTrend.length} venta(s) confirmadas</span>
                  </div>

                  <div className="mt-5">
                    <LineTrendChart points={cumulativeTrend} />
                  </div>
                </div>

                <div>
                  <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                    <div>
                      <h2 className="text-lg font-semibold">
                        {isMarketAdmin ? "Top tiendas del dia" : isCollaborator ? "Top productos del dia" : "Top espacios del dia"}
                      </h2>
                      <p className="text-sm text-muted-foreground">
                        {isMarketAdmin
                          ? "Compara rapidamente cuanto vendio cada Tienda dentro de tu Espacio."
                          : isCollaborator
                            ? "Detecta al instante que productos movieron mas monto hoy."
                            : "Compara el volumen visible por Espacio durante la jornada."}
                      </p>
                    </div>
                  </div>

                  <div className="mt-5 space-y-3">
                    {comparisonBars.slice(0, 6).map((entry) => (
                      <SalesBar
                        key={entry.label}
                        label={entry.label}
                        amount={entry.amount}
                        maxAmount={Math.max(...comparisonBars.map((item) => item.amount), 1)}
                      />
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : null}

          <div className="soft-surface p-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">
                  {isCollaborator ? "Productos con movimiento hoy" : isMarketAdmin ? "Resumen por tienda" : "Totales por Espacio"}
                </h2>
                <p className="text-sm text-muted-foreground">
                  {isCollaborator
                    ? "Revisa cuales productos se movieron hoy y cuanto aporto cada uno a tu venta."
                    : isMarketAdmin
                      ? "Desglose operativo de ventas del dia por Tienda dentro de tu Espacio."
                      : "Resumen del movimiento comercial visible hoy por Espacio."}
                </p>
              </div>
              <span className="soft-chip">
                {isMarketAdmin
                  ? `${collaboratorRows.length} tienda(s) con ventas`
                  : isCollaborator
                    ? `${collaboratorProductRows.length} producto(s) con movimiento`
                    : `${dashboardQuery.data.stores.length} espacio(s) con actividad`}
              </span>
            </div>

            {(isMarketAdmin ? collaboratorRows.length === 0 : isCollaborator ? collaboratorProductRows.length === 0 : dashboardQuery.data.stores.length === 0) ? (
              <div className="mt-4">
                <EmptyState
                  title={isCollaborator ? "Sin productos vendidos hoy" : isMarketAdmin ? "Sin ventas por tienda hoy" : "Sin ventas confirmadas hoy"}
                  description={
                    isCollaborator
                      ? "Cuando existan ventas confirmadas dentro de tu alcance, aqui veras los productos con mejor movimiento."
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
                      <th>{isMarketAdmin ? "Tienda" : isCollaborator ? "Producto" : "Espacio"}</th>
                      <th>{isMarketAdmin ? "Productos vendidos" : isCollaborator ? "Unidades" : "Ventas"}</th>
                      <th>Monto</th>
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
                            <td>{formatMoney(row.totalNet)}</td>
                          </tr>
                        ))
                      : isCollaborator
                        ? collaboratorProductRows.map((row) => (
                            <tr key={row.productName}>
                              <td className="font-medium">{row.productName}</td>
                              <td>{row.quantity}</td>
                              <td>{formatMoney(row.totalAmount)}</td>
                              <td>{formatMoney(row.netAmount)}</td>
                            </tr>
                          ))
                      : dashboardQuery.data.stores.map((store) => (
                          <tr key={store.storeId}>
                            <td className="font-medium">{store.storeName}</td>
                            <td>{resolveStoreSalesCount(store)}</td>
                            <td>{formatMoney(resolveStoreAmount(store))}</td>
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

function buildDashboardStats({
  dashboard,
  details,
  isCollaborator,
  isMarketAdmin,
  collaboratorRows,
  collaboratorProductRows,
}: {
  dashboard: Awaited<ReturnType<typeof getDashboardSummary>>;
  details: Awaited<ReturnType<typeof getSalesTodayDetails>> | undefined;
  isCollaborator: boolean;
  isMarketAdmin: boolean;
  collaboratorRows: Array<{
    collaboratorName: string;
    totalSales: number;
    totalAmount: number;
    totalCommission: number;
    totalNet: number;
  }>;
  collaboratorProductRows: Array<{
    productName: string;
    quantity: number;
    totalAmount: number;
    netAmount: number;
  }>;
}) {
  const salesCount = details?.salesCount ?? dashboard.salesCount;
  const totalAmount = details?.totalAmount ?? dashboard.totalAmount;
  const averageTicket = salesCount > 0 ? totalAmount / salesCount : 0;

  if (isCollaborator) {
    const unitsSold = collaboratorProductRows.reduce((total, row) => total + row.quantity, 0);
    const topProduct = collaboratorProductRows[0];
    return [
      {
        label: "Ventas del dia",
        value: formatMoney(totalAmount),
        helper: `${salesCount} venta(s) confirmadas dentro de tu alcance.`,
      },
      {
        label: "Ticket promedio",
        value: formatMoney(averageTicket),
        helper: "Promedio por venta visible hoy.",
      },
      {
        label: "Unidades vendidas",
        value: String(unitsSold),
        helper: "Suma de unidades confirmadas hoy.",
      },
      {
        label: "Producto lider",
        value: topProduct ? topProduct.productName : "Sin datos",
        helper: topProduct ? `${topProduct.quantity} unidad(es) vendidas hoy.` : "Aun no hay movimiento confirmado.",
      },
    ];
  }

  if (isMarketAdmin) {
    const activeStores = collaboratorRows.length;
    const unitsSold = collaboratorRows.reduce((total, row) => total + row.totalSales, 0);
    return [
      {
        label: "Ventas del dia",
        value: formatMoney(totalAmount),
        helper: `${salesCount} venta(s) confirmadas hoy en tu Espacio.`,
      },
      {
        label: "Ticket promedio",
        value: formatMoney(averageTicket),
        helper: "Promedio por venta confirmada del dia.",
      },
      {
        label: "Tiendas activas",
        value: String(activeStores),
        helper: "Tiendas con al menos una venta visible hoy.",
      },
      {
        label: "Unidades vendidas",
        value: String(unitsSold),
        helper: "Productos vendidos por todas las Tiendas del Espacio.",
      },
    ];
  }

  return [
    {
      label: "Ventas del dia",
      value: formatMoney(dashboard.totalAmount),
      helper: `${dashboard.salesCount} venta(s) visibles hoy.`,
    },
    {
      label: "Ticket promedio",
      value: formatMoney(averageTicket),
      helper: "Promedio de venta visible del dia.",
    },
    {
      label: "Espacios con actividad",
      value: String(dashboard.stores.length),
      helper: "Espacios con movimiento confirmado hoy.",
    },
    {
      label: "Ventas confirmadas",
      value: String(dashboard.salesCount),
      helper: "Total de ventas visibles dentro de tu alcance.",
    },
  ];
}

function SalesBar({
  label,
  amount,
  maxAmount,
}: {
  label: string;
  amount: number;
  maxAmount: number;
}) {
  const width = maxAmount > 0 ? Math.max((amount / maxAmount) * 100, 8) : 0;

  return (
    <div className="grid gap-2 md:grid-cols-[100px_1fr_120px] md:items-center">
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

function LineTrendChart({
  points,
}: {
  points: Array<{
    label: string;
    amount: number;
  }>;
}) {
  if (points.length === 0) {
    return null;
  }

  const width = 640;
  const height = 240;
  const padding = 24;
  const maxAmount = Math.max(...points.map((point) => point.amount), 1);
  const chartWidth = width - padding * 2;
  const chartHeight = height - padding * 2;

  const coordinates = points.map((point, index) => {
    const x = padding + (chartWidth * index) / Math.max(points.length - 1, 1);
    const y = padding + chartHeight - (point.amount / maxAmount) * chartHeight;
    return { ...point, x, y };
  });

  const path = coordinates
    .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
    .join(" ");

  return (
    <div className="rounded-[28px] border border-white/85 bg-white/70 p-4 shadow-sm">
      <svg viewBox={`0 0 ${width} ${height}`} className="h-56 w-full">
        <defs>
          <linearGradient id="sales-trend-line" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="rgba(163,128,255,0.95)" />
            <stop offset="100%" stopColor="rgba(255,153,194,0.92)" />
          </linearGradient>
        </defs>

        {[0.25, 0.5, 0.75, 1].map((ratio) => {
          const y = padding + chartHeight - chartHeight * ratio;
          return <line key={ratio} x1={padding} y1={y} x2={width - padding} y2={y} stroke="rgba(203,194,228,0.35)" strokeDasharray="4 6" />;
        })}

        <path d={path} fill="none" stroke="url(#sales-trend-line)" strokeWidth="4" strokeLinecap="round" />

        {coordinates.map((point) => (
          <g key={`${point.label}-${point.x}`}>
            <circle cx={point.x} cy={point.y} r="5" fill="white" stroke="rgba(163,128,255,0.95)" strokeWidth="3" />
          </g>
        ))}
      </svg>

      <div className="mt-4 grid gap-3 text-xs text-muted-foreground md:grid-cols-3">
        {coordinates.slice(-3).map((point) => (
          <div key={`${point.label}-legend`} className="rounded-[18px] border border-white/80 bg-white/80 px-3 py-2">
            <p className="font-semibold text-foreground">{point.label}</p>
            <p className="mt-1">{formatMoney(point.amount)}</p>
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

function resolveStoreNet(store: {
  netAmount?: number;
  totalNet?: number;
}) {
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

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
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
