import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  createPromotion,
  deletePromotion,
  listPromotions,
  type PromotionCampaign,
  type PromotionCampaignInput,
  type PromotionType,
  updatePromotion,
} from "@/features/promotions/api/promotionApi";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type PromotionFormState = {
  ownerUserId: string;
  name: string;
  type: PromotionType;
  quantity: string;
  promotionalPrice: string;
  percentageDiscount: string;
  minimumPurchaseAmount: string;
  appliesToCash: boolean;
  appliesToDebit: boolean;
  appliesToCredit: boolean;
  appliesToTransfer: boolean;
  startsAt: string;
  endsAt: string;
  active: boolean;
};

const EMPTY_FORM: PromotionFormState = {
  ownerUserId: "",
  name: "",
  type: "QUANTITY_BLOCK",
  quantity: "2",
  promotionalPrice: "",
  percentageDiscount: "",
  minimumPurchaseAmount: "",
  appliesToCash: false,
  appliesToDebit: false,
  appliesToCredit: false,
  appliesToTransfer: false,
  startsAt: "",
  endsAt: "",
  active: true,
};

export function PromotionsPage() {
  const queryClient = useQueryClient();
  const { primaryRole, user } = useSession();
  const isStoreUser = primaryRole === "STORE_USER";
  const [ownerFilter, setOwnerFilter] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState<PromotionCampaign | null>(null);
  const [form, setForm] = useState<PromotionFormState>(EMPTY_FORM);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const usersQuery = useQuery({
    queryKey: ["users", "promotions"],
    queryFn: listUsers,
    enabled: !isStoreUser,
  });
  const storeUsers = useMemo(
    () =>
      isStoreUser && user
        ? [{ id: user.id, fullName: user.fullName, active: true, roles: ["STORE_USER"] }]
        : (usersQuery.data ?? []).filter((candidate) => candidate.active && candidate.roles.includes("STORE_USER")),
    [isStoreUser, user, usersQuery.data],
  );
  const promotionsQuery = useQuery({
    queryKey: ["promotions", ownerFilter],
    queryFn: () => listPromotions({ ownerUserId: ownerFilter ? Number(ownerFilter) : undefined }),
  });

  const saveMutation = useMutation({
    mutationFn: () => {
      const input = buildInput(form, isStoreUser ? user?.id : undefined);
      return editingPromotion ? updatePromotion(editingPromotion.id, input) : createPromotion(input);
    },
    onSuccess: async () => {
      setFeedback({ kind: "success", message: editingPromotion ? "Promocion actualizada." : "Promocion creada." });
      setFormOpen(false);
      setEditingPromotion(null);
      setForm(EMPTY_FORM);
      await queryClient.invalidateQueries({ queryKey: ["promotions"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos guardar la promocion.") }),
  });

  const deleteMutation = useMutation({
    mutationFn: (promotion: PromotionCampaign) => deletePromotion(promotion.id),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Promocion eliminada." });
      await queryClient.invalidateQueries({ queryKey: ["promotions"] });
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos eliminar la promocion.") }),
  });

  const openCreate = () => {
    setEditingPromotion(null);
    setForm({ ...EMPTY_FORM, ownerUserId: isStoreUser && user?.id ? String(user.id) : ownerFilter });
    setFormOpen(true);
  };

  const openEdit = (promotion: PromotionCampaign) => {
    setEditingPromotion(promotion);
    setForm(toFormState(promotion));
    setFormOpen(true);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    saveMutation.mutate();
  };

  return (
    <section>
      <PageHeader
        title="Promociones"
        eyebrow="Motor comercial"
        description="Crea reglas por Tienda y luego asigna productos desde Stock. El POS aplicara la mejor promocion vigente y mostrara su nombre."
        actions={
          <button
            type="button"
            onClick={openCreate}
            className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)]"
          >
            Crear promocion
          </button>
        }
      />

      {feedback ? <div className="mb-5"><FeedbackMessage kind={feedback.kind} message={feedback.message} /></div> : null}

      <div className="soft-surface mb-6 p-5">
        <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
          {!isStoreUser ? (
            <label className="grid gap-2 text-sm">
              <span>Tienda</span>
              <select value={ownerFilter} onChange={(event) => setOwnerFilter(event.target.value)} className="rounded-2xl border border-input bg-background/80 px-3 py-2.5">
                <option value="">Todas las Tiendas visibles</option>
                {storeUsers.map((storeUser) => (
                  <option key={storeUser.id} value={storeUser.id}>
                    {storeUser.fullName}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <div>
              <p className="text-sm text-muted-foreground">Tienda</p>
              <p className="mt-1 font-semibold">{user?.fullName ?? "Mi Tienda"}</p>
            </div>
          )}
          <span className="soft-chip">{promotionsQuery.data?.length ?? 0} promociones</span>
        </div>
      </div>

      {promotionsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando promociones..." /> : null}
      {promotionsQuery.isError ? <FeedbackMessage kind="error" message={getErrorMessage(promotionsQuery.error, "No pudimos cargar las promociones.")} /> : null}

      {promotionsQuery.data?.length === 0 ? (
        <EmptyState title="Sin promociones" description="Crea una promocion y luego asignale productos desde Stock." />
      ) : (
        <div className="soft-surface p-5">
          <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold">Promociones creadas</h2>
              <p className="text-sm text-muted-foreground">Vista compacta para revisar muchas reglas sin perder contexto.</p>
            </div>
            <span className="soft-chip">{promotionsQuery.data?.length ?? 0} registros</span>
          </div>

          <div className="soft-table">
            <table>
              <thead>
                <tr>
                  <th>Promocion</th>
                  <th>Tienda</th>
                  <th>Tipo</th>
                  <th>Vigencia</th>
                  <th>Medios</th>
                  <th>Productos</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {promotionsQuery.data?.map((promotion) => (
                  <tr key={promotion.id}>
                    <td className="font-semibold">{promotion.name}</td>
                    <td>{promotion.ownerFullName}</td>
                    <td>{describePromotionType(promotion)}</td>
                    <td>{describeValidity(promotion.startsAt, promotion.endsAt)}</td>
                    <td>{describePaymentMethods(promotion)}</td>
                    <td>
                      <span className="soft-chip">{promotion.productCount}</span>
                    </td>
                    <td>
                      <span className={promotion.active ? "soft-chip text-emerald-700" : "soft-chip text-muted-foreground"}>
                        {promotion.active ? "Activa" : "Pausada"}
                      </span>
                    </td>
                    <td>
                      <div className="flex flex-wrap gap-2">
                        <button type="button" onClick={() => openEdit(promotion)} className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold shadow-sm">
                          Editar
                        </button>
                        <button
                          type="button"
                          onClick={() => deleteMutation.mutate(promotion)}
                          disabled={deleteMutation.isPending}
                          className="rounded-full border border-rose-100 bg-white/80 px-3 py-1.5 text-xs font-semibold text-rose-700 shadow-sm disabled:opacity-50"
                        >
                          Eliminar
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editingPromotion ? "Editar promocion" : "Crear promocion"}
        description="Define la regla. Los productos se agregan desde Stock con la accion masiva."
        footer={
          <div className="flex justify-end gap-3">
            <button type="button" onClick={() => setFormOpen(false)} className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground">
              Cancelar
            </button>
            <button type="submit" form="promotion-form" disabled={saveMutation.isPending} className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">
              {saveMutation.isPending ? "Guardando..." : "Guardar"}
            </button>
          </div>
        }
      >
        <PromotionForm form={form} setForm={setForm} storeUsers={storeUsers} isStoreUser={isStoreUser} onSubmit={handleSubmit} />
      </Modal>
    </section>
  );
}

function PromotionForm({
  form,
  setForm,
  storeUsers,
  isStoreUser,
  onSubmit,
}: {
  form: PromotionFormState;
  setForm: (form: PromotionFormState) => void;
  storeUsers: Array<{ id: number; fullName: string }>;
  isStoreUser: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form id="promotion-form" onSubmit={onSubmit} className="grid gap-5">
      <div className="grid gap-4 md:grid-cols-2">
        {!isStoreUser ? (
          <label className="grid gap-2 text-sm">
            <span>Tienda</span>
            <select required value={form.ownerUserId} onChange={(event) => setForm({ ...form, ownerUserId: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5">
              <option value="">Selecciona una Tienda</option>
              {storeUsers.map((storeUser) => (
                <option key={storeUser.id} value={storeUser.id}>{storeUser.fullName}</option>
              ))}
            </select>
          </label>
        ) : null}
        <label className="grid gap-2 text-sm">
          <span>Nombre</span>
          <input required maxLength={180} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" placeholder="Ej: 3 photocards paga la mayor" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>Tipo</span>
          <select value={form.type} onChange={(event) => setForm(resetType(form, event.target.value as PromotionType))} className="rounded-2xl border border-input bg-background px-3 py-2.5">
            <option value="QUANTITY_BLOCK">X por precio fijo</option>
            <option value="HIGHEST_PRICE_BUNDLE">X por precio mayor</option>
            <option value="PERCENTAGE_DISCOUNT">% de descuento</option>
            <option value="MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT">% sobre compra mayor a monto</option>
            <option value="PAYMENT_METHOD_DISCOUNT">Descuento por medio de pago</option>
          </select>
        </label>
        {form.type === "QUANTITY_BLOCK" || form.type === "HIGHEST_PRICE_BUNDLE" ? (
          <label className="grid gap-2 text-sm">
            <span>Cantidad X</span>
            <input required min="2" step="1" type="number" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
          </label>
        ) : null}
        {form.type === "QUANTITY_BLOCK" ? (
          <label className="grid gap-2 text-sm">
            <span>Precio fijo</span>
            <input required min="1" step="1" type="number" value={form.promotionalPrice} onChange={(event) => setForm({ ...form, promotionalPrice: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
          </label>
        ) : null}
        {form.type === "PERCENTAGE_DISCOUNT" || form.type === "PAYMENT_METHOD_DISCOUNT" ? (
          <label className="grid gap-2 text-sm">
            <span>Porcentaje</span>
            <input required min="0.01" max="100" step="0.01" type="number" value={form.percentageDiscount} onChange={(event) => setForm({ ...form, percentageDiscount: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
          </label>
        ) : null}
        {form.type === "MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT" ? (
          <>
            <label className="grid gap-2 text-sm">
              <span>Porcentaje</span>
              <input required min="0.01" max="100" step="0.01" type="number" value={form.percentageDiscount} onChange={(event) => setForm({ ...form, percentageDiscount: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
            </label>
            <label className="grid gap-2 text-sm">
              <span>Monto minimo</span>
              <input required min="1" step="1" type="number" value={form.minimumPurchaseAmount} onChange={(event) => setForm({ ...form, minimumPurchaseAmount: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
            </label>
          </>
        ) : null}
      </div>

      {form.type === "PAYMENT_METHOD_DISCOUNT" ? (
        <div className="grid gap-2 text-sm">
          <span>Medios de pago</span>
          <div className="flex flex-wrap gap-2">
            {[
              ["appliesToCash", "Efectivo"],
              ["appliesToDebit", "Debito"],
              ["appliesToCredit", "Credito"],
              ["appliesToTransfer", "Transferencia"],
            ].map(([field, label]) => (
              <label key={field} className="inline-flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-4 py-2.5">
                <input type="checkbox" checked={Boolean(form[field as keyof PromotionFormState])} onChange={(event) => setForm({ ...form, [field]: event.target.checked })} />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </div>
      ) : null}

      <div className="grid gap-4 md:grid-cols-3">
        <label className="grid gap-2 text-sm">
          <span>Inicio</span>
          <input type="date" value={form.startsAt} onChange={(event) => setForm({ ...form, startsAt: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>Termino</span>
          <input type="date" value={form.endsAt} onChange={(event) => setForm({ ...form, endsAt: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="mt-7 inline-flex items-center gap-2 rounded-2xl border border-border/70 bg-background/80 px-4 py-2.5 text-sm">
          <input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} />
          <span>Activa</span>
        </label>
      </div>
    </form>
  );
}

function resetType(form: PromotionFormState, type: PromotionType): PromotionFormState {
  return { ...form, type, promotionalPrice: "", percentageDiscount: "", minimumPurchaseAmount: "", appliesToCash: false, appliesToDebit: false, appliesToCredit: false, appliesToTransfer: false };
}

function buildInput(form: PromotionFormState, forcedOwnerUserId?: number): PromotionCampaignInput {
  return {
    ownerUserId: forcedOwnerUserId ?? Number(form.ownerUserId),
    name: form.name.trim(),
    type: form.type,
    quantity: form.quantity ? Number(form.quantity) : undefined,
    promotionalPrice: form.promotionalPrice ? Number(form.promotionalPrice) : undefined,
    percentageDiscount: form.percentageDiscount ? Number(form.percentageDiscount) : undefined,
    minimumPurchaseAmount: form.minimumPurchaseAmount ? Number(form.minimumPurchaseAmount) : undefined,
    appliesToCash: form.appliesToCash,
    appliesToDebit: form.appliesToDebit,
    appliesToCredit: form.appliesToCredit,
    appliesToTransfer: form.appliesToTransfer,
    startsAt: form.startsAt || undefined,
    endsAt: form.endsAt || undefined,
    active: form.active,
  };
}

function toFormState(promotion: PromotionCampaign): PromotionFormState {
  return {
    ownerUserId: String(promotion.ownerUserId),
    name: promotion.name,
    type: promotion.type,
    quantity: promotion.quantity ? String(promotion.quantity) : "2",
    promotionalPrice: promotion.promotionalPrice ? String(promotion.promotionalPrice) : "",
    percentageDiscount: promotion.percentageDiscount ? String(promotion.percentageDiscount) : "",
    minimumPurchaseAmount: promotion.minimumPurchaseAmount ? String(promotion.minimumPurchaseAmount) : "",
    appliesToCash: promotion.appliesToCash,
    appliesToDebit: promotion.appliesToDebit,
    appliesToCredit: promotion.appliesToCredit,
    appliesToTransfer: promotion.appliesToTransfer,
    startsAt: promotion.startsAt ? toDateInputValue(promotion.startsAt) : "",
    endsAt: promotion.endsAt ? toDateInputValue(promotion.endsAt) : "",
    active: promotion.active,
  };
}

function describePromotionType(promotion: PromotionCampaign) {
  if (promotion.type === "QUANTITY_BLOCK") {
    return `${promotion.quantity} por ${formatCurrency(promotion.promotionalPrice ?? 0)}`;
  }
  if (promotion.type === "HIGHEST_PRICE_BUNDLE") {
    return `${promotion.quantity} por precio mayor`;
  }
  if (promotion.type === "PAYMENT_METHOD_DISCOUNT") {
    return `${promotion.percentageDiscount}% por medio de pago`;
  }
  if (promotion.type === "MIN_PURCHASE_AMOUNT_PERCENTAGE_DISCOUNT") {
    return `${promotion.percentageDiscount}% sobre ${formatCurrency(promotion.minimumPurchaseAmount ?? 0)}`;
  }
  return `${promotion.percentageDiscount}% descuento`;
}

function describePaymentMethods(promotion: PromotionCampaign) {
  if (promotion.type !== "PAYMENT_METHOD_DISCOUNT") {
    return "Todos";
  }
  const methods = [
    promotion.appliesToCash ? "efectivo" : null,
    promotion.appliesToDebit ? "debito" : null,
    promotion.appliesToCredit ? "credito" : null,
    promotion.appliesToTransfer ? "transferencia" : null,
  ].filter(Boolean);
  return methods.join(", ") || "Sin medio";
}

function describeValidity(startsAt: string | null, endsAt: string | null) {
  if (!startsAt && !endsAt) {
    return "Indefinida";
  }
  const start = startsAt ? formatDateOnly(startsAt) : "Hoy";
  const end = endsAt ? formatDateOnly(endsAt) : "sin termino";
  return `${start} a ${end}`;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(value);
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat("es-CL", { dateStyle: "medium" }).format(new Date(value));
}

function toDateInputValue(value: string) {
  return value.slice(0, 10);
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
