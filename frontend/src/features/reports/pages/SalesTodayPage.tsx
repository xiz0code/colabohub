import { useQuery } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { getSalesTodayDetails } from "@/features/reports/api/reportApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

export function SalesTodayPage() {
  const { primaryRole } = useSession();
  const salesTodayQuery = useQuery({
    queryKey: ["reports", "sales", "today", "details"],
    queryFn: getSalesTodayDetails,
  });
  const isCollaborator = primaryRole === "STORE_USER";

  return (
    <section>
      <PageHeader
        title={isCollaborator ? "Mis ventas" : "Ventas del dia"}
        description={
          isCollaborator
            ? "Vista de solo lectura con ventas confirmadas dentro de tu alcance permitido."
            : "Detalle de ventas confirmadas del dia con totales agregados y desglose por Tienda."
        }
        eyebrow={isCollaborator ? "Consulta personal" : "Operacion comercial"}
      />

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

          <div className="soft-surface p-6">
            <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
              <div>
                <h2 className="text-lg font-semibold">Totales por Tienda</h2>
                <p className="text-sm text-muted-foreground">Vista agregada para entender rapidamente el volumen visible del dia.</p>
              </div>
              <span className="soft-chip">{salesTodayQuery.data.stores.length} Tienda(s)</span>
            </div>

            {salesTodayQuery.data.stores.length === 0 ? (
              <div className="mt-4">
                <EmptyState
                  title={isCollaborator ? "No tienes ventas visibles hoy" : "No hay ventas confirmadas hoy"}
                  description={
                    isCollaborator
                      ? "Cuando existan ventas dentro de tu alcance permitido, apareceran aqui agrupadas por Tienda."
                      : "Cuando existan ventas confirmadas apareceran aqui agrupadas por Tienda."
                  }
                />
              </div>
            ) : (
              <div className="soft-table mt-4">
                <table>
                  <thead>
                    <tr>
                      <th>Tienda</th>
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
                        <td>{store.totalSales}</td>
                        <td>{formatMoney(store.totalAmount)}</td>
                        <td>{formatMoney(store.totalCommission)}</td>
                        <td>{formatMoney(store.totalNet)}</td>
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
              {isCollaborator ? "Tu lectura diaria se mantiene sin acciones de edicion." : "Consulta cada venta con un desglose suave por Tienda."}
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
                              <th>Tienda</th>
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
                              <th>Colaborador</th>
                              <th>Tienda</th>
                              <th>Cant.</th>
                              <th>Subtotal</th>
                              <th>Neto</th>
                            </tr>
                          </thead>
                          <tbody>
                            {sale.items.map((item) => (
                              <tr key={item.itemId}>
                                <td className="font-medium">{item.productName}</td>
                                <td>{item.collaboratorName ?? "Sin colaborador"}</td>
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
