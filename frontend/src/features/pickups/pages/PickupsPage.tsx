import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { listPickups, createPickup, checkoutPickup, collectPickup, cancelPickup, type Pickup, type PickupStatus } from "@/features/pickups/api/pickupsApi";
import { usePosLauncher } from "@/features/sales/components/PosLauncherProvider";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type PickupFormState = {
  storeId: string;
  pickupNumber: string;
  customerName: string;
  description: string;
  payable: boolean;
  amountDue: string;
};

const EMPTY_FORM: PickupFormState = {
  storeId: "",
  pickupNumber: "",
  customerName: "",
  description: "",
  payable: false,
  amountDue: "",
};

export function PickupsPage() {
  const queryClient = useQueryClient();
  const { user, primaryRole } = useSession();
  const { openPosWithSale } = usePosLauncher();
  const isStoreUser = primaryRole === "STORE_USER";
  const canCreate = primaryRole === "ADMIN_MARKET" || primaryRole === "STORE_USER";
  const canOperatePickup = primaryRole === "ADMIN_MARKET" || primaryRole === "SELLER";
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<PickupStatus | "ALL">("ALL");
  const [selectedStoreId, setSelectedStoreId] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState<PickupFormState>(EMPTY_FORM);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);

  const usersQuery = useQuery({
    queryKey: ["users", "pickup-stores"],
    queryFn: listUsers,
    enabled: !isStoreUser,
  });

  const storeOptions = useMemo(() => {
    if (isStoreUser && user) {
      return [{ value: String(user.storeIds[0] ?? ""), label: user.fullName }];
    }

    return (usersQuery.data ?? [])
      .filter((candidate) => candidate.active && candidate.roles.includes("STORE_USER"))
      .filter((candidate) => (user?.activeMarketId ? candidate.marketIds.includes(user.activeMarketId) : true))
      .map((candidate) => ({
        value: String(candidate.storeIds[0] ?? ""),
        label: candidate.fullName,
      }))
      .filter((option) => option.value)
      .sort((left, right) => left.label.localeCompare(right.label));
  }, [isStoreUser, user, usersQuery.data]);

  const pickupsQuery = useQuery({
    queryKey: ["pickups", search, status, selectedStoreId],
    queryFn: () => listPickups({ query: search || undefined, status, storeId: selectedStoreId ? Number(selectedStoreId) : null }),
  });

  const inferredStoreOption = isStoreUser ? storeOptions[0] ?? null : null;

  const createMutation = useMutation({
    mutationFn: () =>
      createPickup({
        ...(isStoreUser ? {} : { storeId: Number(form.storeId) }),
        pickupNumber: form.pickupNumber.trim(),
        customerName: form.customerName.trim(),
        description: form.description.trim(),
        payable: form.payable,
        ...(form.payable ? { amountDue: Number(form.amountDue) } : {}),
      }),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Retiro creado correctamente." });
      setCreateOpen(false);
      setForm(isStoreUser && storeOptions[0] ? { ...EMPTY_FORM, storeId: storeOptions[0].value } : EMPTY_FORM);
      await queryClient.invalidateQueries({ queryKey: ["pickups"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos crear el retiro.") }),
  });

  const collectMutation = useMutation({
    mutationFn: (pickupId: number) => collectPickup(pickupId),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Retiro marcado como retirado." });
      await queryClient.invalidateQueries({ queryKey: ["pickups"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos marcar el retiro.") }),
  });

  const checkoutMutation = useMutation({
    mutationFn: (pickupId: number) => checkoutPickup(pickupId),
    onSuccess: async (payload) => {
      openPosWithSale(payload.sale);
      setFeedback({ kind: "info", message: "Retiro cargado en el POS. Puedes cobrarlo y agregar mas productos." });
      await queryClient.invalidateQueries({ queryKey: ["pickups"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos preparar el cobro del retiro.") }),
  });

  const cancelMutation = useMutation({
    mutationFn: (pickupId: number) => cancelPickup(pickupId),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Retiro anulado correctamente." });
      await queryClient.invalidateQueries({ queryKey: ["pickups"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos anular el retiro.") }),
  });

  const visiblePickups = pickupsQuery.data ?? [];
  const pendingCount = visiblePickups.filter((pickup) => pickup.status === "PENDING").length;
  const checkoutInProgressCount = visiblePickups.filter((pickup) => pickup.status === "CHECKOUT_IN_PROGRESS").length;
  const collectedCount = visiblePickups.filter((pickup) => pickup.status === "COLLECTED").length;
  const payableCount = visiblePickups.filter((pickup) => pickup.payable && pickup.status !== "COLLECTED").length;
  const payableAmount = visiblePickups
    .filter((pickup) => pickup.payable && pickup.status !== "COLLECTED")
    .reduce((total, pickup) => total + (pickup.amountDue ?? 0), 0);
  const upcomingActionLabel = payableCount > 0 ? "Cobrar en POS" : pendingCount > 0 ? "Entregar en mostrador" : "Sin urgencias";

  return (
    <section className="space-y-6">
      <PageHeader
        title="Retiros"
        description="Registra retiros listos para entrega y, cuando corresponda, cargalos al POS para cobrarlos junto con otras compras."
        eyebrow="Operacion en mostrador"
      />

      {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

      <div className="soft-surface p-6">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Panel de retiros</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Controla pedidos listos para entregar sin mezclar inventario del Espacio con ventas externas o personalizadas.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            {!isStoreUser ? (
              <select
                value={selectedStoreId}
                onChange={(event) => setSelectedStoreId(event.target.value)}
                className="min-w-[210px] rounded-full border border-white/85 bg-white/80 px-4 py-3 text-sm shadow-sm outline-none"
              >
                <option value="">Todas las Tiendas</option>
                {storeOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            ) : null}

            <select
              value={status}
              onChange={(event) => setStatus(event.target.value as PickupStatus | "ALL")}
              className="rounded-full border border-white/85 bg-white/80 px-4 py-3 text-sm shadow-sm outline-none"
            >
              <option value="ALL">Todos los estados</option>
              <option value="PENDING">Pendientes</option>
              <option value="CHECKOUT_IN_PROGRESS">Cobro en curso</option>
              <option value="COLLECTED">Retirados</option>
              <option value="CANCELLED">Cancelados</option>
            </select>

            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por numero, cliente o descripcion"
              className="min-w-[260px] rounded-full border border-white/85 bg-white/80 px-4 py-3 text-sm shadow-sm outline-none"
            />

            {canCreate ? (
              <button
                type="button"
                onClick={() => {
                  setForm(isStoreUser && storeOptions[0] ? { ...EMPTY_FORM, storeId: storeOptions[0].value } : EMPTY_FORM);
                  setCreateOpen(true);
                }}
                disabled={isStoreUser && !inferredStoreOption}
                className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)]"
              >
                Nuevo retiro
              </button>
            ) : null}
          </div>
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-3">
          <SummaryCard label="Retiros visibles" value={String(visiblePickups.length)} />
          <SummaryCard label="Pendientes" value={String(pendingCount)} />
          <SummaryCard label="Por pagar" value={String(payableCount)} />
        </div>

        <div className="mt-3 grid gap-3 md:grid-cols-3">
          <SummaryCard label="Cobro en curso" value={String(checkoutInProgressCount)} />
          <SummaryCard label="Retirados" value={String(collectedCount)} />
          <SummaryCard label="Monto por cobrar" value={formatMoney(payableAmount)} />
        </div>

        <div className="mt-6 grid gap-4 xl:grid-cols-[1.2fr_1fr]">
          <div className="soft-subtle-surface rounded-[28px] p-5">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Flujo recomendado</p>
                <h3 className="mt-2 text-lg font-semibold tracking-tight text-foreground">Retiro pagado vs retiro por pagar</h3>
              </div>
              <span className="soft-chip">{upcomingActionLabel}</span>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <QuickGuideCard
                title="Ya esta pagado"
                description="Solo registralo y, cuando llegue la persona, usa 'Marcar retirado' para cerrar la entrega sin pasar por caja."
              />
              <QuickGuideCard
                title="Aun no esta pagado"
                description="Usa 'Cobrar retiro' para precargarlo en el POS. Desde ahi puedes sumar mas productos y cerrar todo en una sola venta."
              />
            </div>
          </div>

          <div className="soft-subtle-surface rounded-[28px] p-5">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Resumen del turno</p>
            <h3 className="mt-2 text-lg font-semibold tracking-tight text-foreground">Lo que requiere atencion hoy</h3>
            <div className="mt-4 space-y-3">
              <ActionRow label="Retiros por entregar" value={String(pendingCount)} tone="neutral" />
              <ActionRow label="Retiros que deben pasar por caja" value={String(payableCount)} tone="accent" />
              <ActionRow label="Retiros ya cerrados" value={String(collectedCount)} tone="success" />
            </div>
          </div>
        </div>

        <div className="soft-table mt-6">
          <table>
            <thead>
              <tr>
                <th>Numero</th>
                <th>Cliente</th>
                <th>Tienda</th>
                <th>Descripcion</th>
                <th>Estado</th>
                <th>Pago</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {visiblePickups.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <EmptyState
                      title="No hay retiros para mostrar"
                      description="Cuando registres retiros por web, Instagram o WhatsApp, apareceran aqui listos para seguimiento."
                    />
                  </td>
                </tr>
              ) : (
                visiblePickups.map((pickup) => (
                  <tr key={pickup.id}>
                    <td>
                      <div className="font-medium">{pickup.pickupNumber}</div>
                      <div className="text-xs text-muted-foreground">{formatDate(pickup.createdAt)}</div>
                    </td>
                    <td>{pickup.customerName}</td>
                    <td>{pickup.storeName}</td>
                    <td>{pickup.description}</td>
                    <td>
                      <span className="soft-chip">{formatPickupStatus(pickup.status)}</span>
                    </td>
                    <td>{pickup.payable ? `Por pagar ${formatMoney(pickup.amountDue ?? 0)}` : "Pagado"}</td>
                    <td>
                      <div className="flex flex-wrap gap-2">
                        {pickup.payable ? (
                          <button
                            type="button"
                            onClick={() => checkoutMutation.mutate(pickup.id)}
                            disabled={!canOperatePickup || pickup.status === "COLLECTED" || pickup.status === "CANCELLED" || checkoutMutation.isPending}
                            className="rounded-full border border-violet-200 bg-violet-50 px-3 py-1.5 text-xs font-semibold text-violet-700 disabled:opacity-50"
                          >
                            {pickup.status === "CHECKOUT_IN_PROGRESS" ? "Abrir cobro" : "Cobrar retiro"}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => collectMutation.mutate(pickup.id)}
                            disabled={!canOperatePickup || pickup.status === "COLLECTED" || pickup.status === "CANCELLED" || collectMutation.isPending}
                            className="rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 disabled:opacity-50"
                          >
                            Marcar retirado
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => cancelMutation.mutate(pickup.id)}
                          disabled={!canOperatePickup || pickup.status === "COLLECTED" || pickup.status === "CANCELLED" || cancelMutation.isPending}
                          className="rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 disabled:opacity-50"
                        >
                          Anular retiro
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Nuevo retiro"
        description="Registra un retiro pendiente desde web, Instagram, WhatsApp u otro canal externo."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setCreateOpen(false)}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground"
            >
              Cancelar
            </button>
            <button
              type="submit"
              form="pickup-create-form"
              disabled={createMutation.isPending}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {createMutation.isPending ? "Guardando..." : "Crear retiro"}
            </button>
          </div>
        }
      >
        <form
          id="pickup-create-form"
          onSubmit={(event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            setFeedback(null);
            createMutation.mutate();
          }}
          className="grid gap-5"
        >
          <div className="grid gap-5 md:grid-cols-2">
            {isStoreUser ? (
              <div className="grid gap-2 text-sm">
                <span>Tienda</span>
                <div className="rounded-2xl border border-input bg-background/80 px-3 py-2.5 text-foreground">
                  {inferredStoreOption?.label ?? "Tu Tienda asignada"}
                </div>
              </div>
            ) : (
              <label className="grid gap-2 text-sm">
                <span>Tienda</span>
                <select
                  required
                  value={form.storeId}
                  onChange={(event) => setForm((current) => ({ ...current, storeId: event.target.value }))}
                  className="rounded-2xl border border-input bg-background px-3 py-2.5"
                >
                  <option value="">Selecciona una Tienda</option>
                  {storeOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            )}

            <label className="grid gap-2 text-sm">
              <span>Numero de retiro</span>
              <input
                required
                value={form.pickupNumber}
                onChange={(event) => setForm((current) => ({ ...current, pickupNumber: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2.5"
                placeholder="Ej. IG115 o #1004"
              />
            </label>

            <label className="grid gap-2 text-sm">
              <span>Nombre de quien retira</span>
              <input
                required
                value={form.customerName}
                onChange={(event) => setForm((current) => ({ ...current, customerName: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2.5"
              />
            </label>

            <label className="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/80 px-4 py-3 text-sm">
              <input
                type="checkbox"
                checked={form.payable}
                onChange={(event) => setForm((current) => ({ ...current, payable: event.target.checked, amountDue: event.target.checked ? current.amountDue : "" }))}
              />
              <span>Retiro por pagar</span>
            </label>
          </div>

          <label className="grid gap-2 text-sm">
            <span>Descripcion</span>
            <textarea
              required
              rows={4}
              value={form.description}
              onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
              className="rounded-[24px] border border-input bg-background px-3 py-3"
              placeholder="Describe brevemente lo que la persona viene a retirar."
            />
          </label>

          {form.payable ? (
            <label className="grid gap-2 text-sm md:max-w-xs">
              <span>Monto por cobrar</span>
              <input
                required
                min="1"
                step="1"
                type="number"
                value={form.amountDue}
                onChange={(event) => setForm((current) => ({ ...current, amountDue: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2.5"
              />
            </label>
          ) : null}
        </form>
      </Modal>
    </section>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="soft-subtle-surface rounded-[22px] p-4">
      <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-base font-semibold text-foreground">{value}</p>
    </div>
  );
}

function QuickGuideCard({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-[24px] border border-white/80 bg-white/75 p-4 shadow-sm">
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <p className="mt-2 text-sm text-muted-foreground">{description}</p>
    </div>
  );
}

function ActionRow({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: "neutral" | "accent" | "success";
}) {
  const toneClassName =
    tone === "accent"
      ? "bg-violet-50 text-violet-700 border-violet-200"
      : tone === "success"
        ? "bg-emerald-50 text-emerald-700 border-emerald-200"
        : "bg-white/80 text-foreground border-white/80";

  return (
    <div className={`flex items-center justify-between rounded-[22px] border px-4 py-3 ${toneClassName}`}>
      <span className="text-sm font-medium">{label}</span>
      <span className="text-sm font-semibold">{value}</span>
    </div>
  );
}

function formatPickupStatus(status: Pickup["status"]) {
  switch (status) {
    case "PENDING":
      return "Pendiente";
    case "CHECKOUT_IN_PROGRESS":
      return "Cobro en curso";
    case "COLLECTED":
      return "Retirado";
    case "CANCELLED":
      return "Cancelado";
    default:
      return status;
  }
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDate(value: string) {
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
