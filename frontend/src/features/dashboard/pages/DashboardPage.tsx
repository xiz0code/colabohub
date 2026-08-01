import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowDownRight, ArrowUpRight, PackageSearch, ShoppingBag } from "lucide-react";
import { Link } from "react-router-dom";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

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
  const weeklyTrend = dashboardQuery.data?.trend ?? [];
  const paymentMethods = dashboardQuery.data?.paymentMethods ?? [];
  const salesChange = dashboardQuery.data?.salesChangePercentage ?? 0;
  const isPositiveChange = salesChange >= 0;
  const recentCollaboratorSales = isCollaborator
    ? [...(salesDetailsQuery.data?.sales ?? [])]
        .sort((left, right) => new Date(right.confirmedAt).getTime() - new Date(left.confirmedAt).getTime())
    : [];
  const alertItems = dashboardQuery.data
    ? [
        {
          label: "Stock crítico",
          value: dashboardQuery.data.lowStockProducts,
          description: isCollaborator ? "Productos de tu tienda con 5 unidades o menos." : "Productos visibles con 5 unidades o menos.",
          to: "/products",
          icon: PackageSearch,
          tone: "amber" as const,
        },
        {
          label: "Retiros pendientes",
          value: dashboardQuery.data.pendingPickups,
          description: "Retiros pendientes o con cobro en curso.",
          to: "/pickups",
          icon: ShoppingBag,
          tone: "violet" as const,
        },
      ]
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
          <div className="border-y border-violet-100/80 bg-white/60 px-5 py-5 shadow-[0_16px_44px_rgba(126,94,173,0.08)]">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-violet-700">Pulso comercial de hoy</p>
                <h2 className="mt-2 text-xl font-bold text-foreground">
                  {formatMoney(dashboardQuery.data.totalAmount)} vendidos hasta ahora
                </h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Ayer cerraste con {formatMoney(dashboardQuery.data.previousDayAmount)} en {dashboardQuery.data.previousDaySalesCount} venta(s).
                </p>
              </div>
              <div
                className={[
                  "inline-flex w-fit items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold",
                  isPositiveChange
                    ? "border-emerald-100 bg-emerald-50 text-emerald-800"
                    : "border-rose-100 bg-rose-50 text-rose-800",
                ].join(" ")}
              >
                {isPositiveChange ? <ArrowUpRight className="h-4 w-4" /> : <ArrowDownRight className="h-4 w-4" />}
                {formatPercentage(salesChange)} respecto de ayer
              </div>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => (
              <StatCard key={stat.label} {...stat} />
            ))}
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.35fr_0.65fr]">
            <div className="soft-surface min-w-0 p-6">
              <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-violet-700">Últimos 7 días</p>
                  <h2 className="mt-2 text-lg font-semibold">Evolución de ventas</h2>
                  <p className="text-sm text-muted-foreground">Compara el ritmo diario y detecta cambios antes del cierre mensual.</p>
                </div>
                <span className="soft-chip">{weeklyTrend.reduce((total, point) => total + point.salesCount, 0)} venta(s)</span>
              </div>
              <div className="mt-5 h-[300px]">
                <WeeklySalesChart data={weeklyTrend} />
              </div>
            </div>

            <div className="soft-surface min-w-0 p-6">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-sky-700">Cuadratura de hoy</p>
                <h2 className="mt-2 text-lg font-semibold">Medios de pago</h2>
                <p className="text-sm text-muted-foreground">Distribución del monto vendido durante la jornada.</p>
              </div>
              <div className="mt-5 h-[210px]">
                <PaymentMethodChart data={paymentMethods} />
              </div>
              <div className="mt-3 space-y-2">
                {paymentMethods.map((method, index) => (
                  <div key={method.paymentMethod} className="flex items-center justify-between gap-3 text-sm">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: PAYMENT_COLORS[index % PAYMENT_COLORS.length] }} />
                      <span className="truncate text-muted-foreground">{translatePaymentMethod(method.paymentMethod)}</span>
                    </div>
                    <span className="font-semibold">{formatMoney(method.totalAmount)}</span>
                  </div>
                ))}
                {paymentMethods.length === 0 ? <p className="text-sm text-muted-foreground">Aún no hay pagos confirmados hoy.</p> : null}
              </div>
            </div>
          </div>

          <div className="grid gap-6 xl:grid-cols-[0.75fr_1.25fr]">
            <div className="soft-surface p-6">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-amber-600" />
                <h2 className="text-lg font-semibold">Atención requerida</h2>
              </div>
              <p className="mt-1 text-sm text-muted-foreground">Acciones que conviene revisar durante la jornada.</p>
              <div className="mt-5 space-y-3">
                {alertItems.map((alert) => (
                  <AlertLink key={alert.label} {...alert} />
                ))}
              </div>
            </div>

            <div className="soft-surface p-6">
              <div>
                <h2 className="text-lg font-semibold">
                  {isMarketAdmin ? "Top tiendas del día" : isCollaborator ? "Top productos del día" : "Top espacios del día"}
                </h2>
                <p className="text-sm text-muted-foreground">
                  {isMarketAdmin
                    ? "Compara rápidamente el rendimiento de cada Tienda."
                    : isCollaborator
                      ? "Identifica los productos que más aportan a tus ventas."
                      : "Compara el volumen comercial entre Espacios."}
                </p>
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
                {comparisonBars.length === 0 ? <p className="text-sm text-muted-foreground">Sin movimiento confirmado hoy.</p> : null}
              </div>
            </div>
          </div>

          {isCollaborator ? (
            <div className="soft-surface p-6">
              <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-emerald-700">Actividad reciente</p>
                  <h2 className="mt-2 text-lg font-semibold">Últimas ventas confirmadas de hoy</h2>
                  <p className="text-sm text-muted-foreground">
                    Revisa rápidamente las ventas que forman el resultado de la jornada vigente.
                  </p>
                </div>
                <Link to="/sales/today" className="text-sm font-semibold text-violet-700 hover:text-violet-900">
                  Ver detalle completo
                </Link>
              </div>

              {salesDetailsQuery.isLoading ? (
                <div className="mt-5">
                  <FeedbackMessage kind="info" message="Cargando ventas recientes..." />
                </div>
              ) : recentCollaboratorSales.length === 0 ? (
                <div className="mt-5">
                  <EmptyState
                    title="Sin ventas confirmadas hoy"
                    description="Cuando se confirme una venta de tu Tienda, aparecerá aquí con sus productos y monto."
                  />
                </div>
              ) : (
                <div className="mt-5 grid gap-3 lg:grid-cols-2">
                  {recentCollaboratorSales.map((sale) => (
                    <article key={sale.saleId} className="rounded-lg border border-border/60 bg-white/80 p-4 shadow-sm">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold text-foreground">{sale.saleNumber}</p>
                          <p className="mt-1 text-sm text-muted-foreground">{formatDateTime(sale.confirmedAt)}</p>
                        </div>
                        <span className="soft-chip">{formatMoney(sale.totalAmount)}</span>
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2">
                        {sale.items.map((item) => (
                          <span
                            key={item.itemId}
                            className="rounded-full bg-secondary/70 px-3 py-1 text-xs font-semibold text-muted-foreground"
                          >
                            {item.quantity} x {item.productName}
                          </span>
                        ))}
                      </div>
                    </article>
                  ))}
                </div>
              )}
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

const PAYMENT_COLORS = ["#8b5cf6", "#0ea5e9", "#10b981", "#f59e0b", "#f43f5e"];

function WeeklySalesChart({
  data,
}: {
  data: Array<{ date: string; salesCount: number; totalAmount: number; totalNet: number }>;
}) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 10, right: 8, left: -18, bottom: 0 }}>
        <defs>
          <linearGradient id="dashboardSalesArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.34} />
            <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="4 6" vertical={false} stroke="rgba(148, 163, 184, 0.22)" />
        <XAxis dataKey="date" tickFormatter={formatShortDate} tickLine={false} axisLine={false} fontSize={12} />
        <YAxis tickFormatter={formatCompactMoney} tickLine={false} axisLine={false} fontSize={12} width={70} />
        <Tooltip
          labelFormatter={(label) => formatLongDate(String(label))}
          formatter={(value) => [formatMoney(Number(value)), "Ventas"]}
          contentStyle={{ borderRadius: 8, border: "1px solid rgba(226,232,240,.9)", boxShadow: "0 12px 30px rgba(88,72,120,.12)" }}
        />
        <Area type="monotone" dataKey="totalAmount" stroke="#8b5cf6" strokeWidth={3} fill="url(#dashboardSalesArea)" />
      </AreaChart>
    </ResponsiveContainer>
  );
}

function PaymentMethodChart({
  data,
}: {
  data: Array<{ paymentMethod: string; salesCount: number; totalAmount: number }>;
}) {
  if (data.length === 0) {
    return (
      <div className="flex h-full items-center justify-center rounded-lg border border-dashed border-border/70 text-sm text-muted-foreground">
        Sin pagos confirmados
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height="100%">
      <PieChart>
        <Pie data={data} dataKey="totalAmount" nameKey="paymentMethod" innerRadius={54} outerRadius={82} paddingAngle={3}>
          {data.map((entry, index) => (
            <Cell key={entry.paymentMethod} fill={PAYMENT_COLORS[index % PAYMENT_COLORS.length]} />
          ))}
        </Pie>
        <Tooltip formatter={(value) => formatMoney(Number(value))} />
      </PieChart>
    </ResponsiveContainer>
  );
}

function AlertLink({
  label,
  value,
  description,
  to,
  icon: Icon,
  tone,
}: {
  label: string;
  value: number;
  description: string;
  to: string;
  icon: typeof AlertTriangle;
  tone: "amber" | "violet";
}) {
  const toneClasses =
    tone === "amber"
      ? "border-amber-100 bg-amber-50/70 text-amber-900"
      : "border-violet-100 bg-violet-50/70 text-violet-900";

  return (
    <Link to={to} className={["flex items-center gap-3 rounded-lg border p-4 transition hover:-translate-y-0.5 hover:shadow-sm", toneClasses].join(" ")}>
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/75">
        <Icon className="h-5 w-5" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-3">
          <p className="font-semibold">{label}</p>
          <span className="text-xl font-bold">{value}</span>
        </div>
        <p className="mt-1 text-xs opacity-75">{description}</p>
      </div>
    </Link>
  );
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatCompactMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

function formatPercentage(value: number) {
  const sign = value > 0 ? "+" : "";
  return `${sign}${new Intl.NumberFormat("es-CL", { maximumFractionDigits: 1 }).format(value)}%`;
}

function formatShortDate(value: string) {
  const date = parseLocalDate(value);
  return new Intl.DateTimeFormat("es-CL", { weekday: "short" }).format(date).replace(".", "");
}

function formatLongDate(value: string) {
  return new Intl.DateTimeFormat("es-CL", { weekday: "long", day: "numeric", month: "long" }).format(parseLocalDate(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function translatePaymentMethod(value: string) {
  return {
    CASH: "Efectivo",
    DEBITO: "Débito",
    DEBIT: "Débito",
    CREDIT: "Crédito",
    TRANSFER: "Transferencia",
    OTHER: "Otro",
    UNKNOWN: "Sin definir",
  }[value] ?? value;
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

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
