import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createMarket,
  listMarkets,
  type Market,
  type UpsertMarketInput,
  updateMarket,
  updateMarketStatus,
} from "@/features/markets/api/marketApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type MarketFormState = {
  name: string;
  email: string;
  phone: string;
  contactName: string;
  description: string;
  active: boolean;
};

const initialFormState: MarketFormState = {
  name: "",
  email: "",
  phone: "",
  contactName: "",
  description: "",
  active: true,
};

export function MarketsPage() {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMarket, setEditingMarket] = useState<Market | null>(null);
  const [formState, setFormState] = useState<MarketFormState>(initialFormState);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const marketsQuery = useQuery({
    queryKey: ["markets"],
    queryFn: listMarkets,
  });

  const activeCount = useMemo(() => marketsQuery.data?.filter((market) => market.active).length ?? 0, [marketsQuery.data]);

  const createMutation = useMutation({
    mutationFn: createMarket,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["markets"] });
      setFeedback({ kind: "success", message: "Espacio creado correctamente." });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible crear el Espacio.") });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ marketId, input }: { marketId: number; input: UpsertMarketInput }) => updateMarket(marketId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["markets"] });
      setFeedback({ kind: "success", message: "Espacio actualizado correctamente." });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar el Espacio.") });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ marketId, active }: { marketId: number; active: boolean }) => updateMarketStatus(marketId, active),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["markets"] });
      setFeedback({
        kind: "success",
        message: variables.active ? "Espacio activado correctamente." : "Espacio desactivado correctamente.",
      });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cambiar el estado del Espacio.") });
    },
  });

  const openCreateModal = () => {
    setEditingMarket(null);
    setFormState(initialFormState);
    setIsModalOpen(true);
  };

  const openEditModal = (market: Market) => {
    setEditingMarket(market);
    setFormState({
      name: market.name,
      email: market.email,
      phone: market.phone ?? "",
      contactName: market.contactName ?? "",
      description: market.description ?? "",
      active: market.active,
    });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingMarket(null);
    setFormState(initialFormState);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload: UpsertMarketInput = {
      name: formState.name,
      email: formState.email,
      phone: formState.phone || undefined,
      contactName: formState.contactName || undefined,
      description: formState.description || undefined,
      active: formState.active,
    };

    if (editingMarket) {
      updateMutation.mutate({ marketId: editingMarket.id, input: payload });
      return;
    }

    createMutation.mutate(payload);
  };

  return (
    <section>
      <PageHeader
        title="Espacios"
        description="Administra los Espacios del sistema, su acceso visible y su disponibilidad operativa desde una experiencia mas limpia y ejecutiva."
        eyebrow="Administrador General"
      />

      <div className="space-y-6">
        {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

        <div className="grid gap-4 lg:grid-cols-3">
          <MetricCard label="Espacios registrados" value={String(marketsQuery.data?.length ?? 0)} helper="Base completa del sistema." />
          <MetricCard label="Espacios activos" value={String(activeCount)} helper="Disponibles hoy para operar." />
          <div className="soft-surface p-6">
            <p className="text-sm text-muted-foreground">Administracion</p>
            <p className="mt-2 text-xl font-semibold tracking-tight">Alta y mantenimiento centralizado</p>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Solo el Administrador General puede crear o ajustar Espacios.
            </p>
            <button
              type="button"
              onClick={openCreateModal}
              className="mt-5 rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)]"
            >
              Nuevo Espacio
            </button>
          </div>
        </div>

        <div className="soft-surface p-6">
          <div className="mb-5 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Listado de Espacios</h2>
              <p className="text-sm text-muted-foreground">
                La entidad interna sigue siendo Market, pero la experiencia visible ya habla en lenguaje de Espacio.
              </p>
            </div>
            <button
              type="button"
              onClick={openCreateModal}
              className="rounded-full border border-white/85 bg-white/70 px-4 py-2 text-sm font-semibold shadow-sm transition hover:-translate-y-0.5"
            >
              Crear Espacio
            </button>
          </div>

          {marketsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando Espacios..." /> : null}
          {marketsQuery.isError ? (
            <FeedbackMessage kind="error" message={getErrorMessage(marketsQuery.error, "No fue posible cargar los Espacios.")} />
          ) : null}

          {!marketsQuery.isLoading && !marketsQuery.isError && (marketsQuery.data?.length ?? 0) === 0 ? (
            <EmptyState
              title="Todavia no hay Espacios"
              description="Crea el primer Espacio para habilitar su administracion y luego asignar Tiendas."
            />
          ) : (
            <div className="soft-table">
              <table>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Correo de login</th>
                    <th>Contacto</th>
                    <th>Telefono</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {marketsQuery.data?.map((market) => (
                    <tr key={market.id}>
                      <td>
                        <div>
                          <p className="font-semibold">{market.name}</p>
                          <p className="text-sm text-muted-foreground">{market.description || "Sin descripcion registrada."}</p>
                        </div>
                      </td>
                      <td>{market.email}</td>
                      <td>{market.contactName || "Sin contacto"}</td>
                      <td>{market.phone || "Sin telefono"}</td>
                      <td>
                        <span
                          className={[
                            "inline-flex rounded-full border px-3 py-1 text-xs font-semibold shadow-sm",
                            market.active
                              ? "border-emerald-200/90 bg-emerald-50/90 text-emerald-800"
                              : "border-slate-200/90 bg-slate-100/90 text-slate-700",
                          ].join(" ")}
                        >
                          {market.active ? "Activa" : "Inactiva"}
                        </span>
                      </td>
                      <td>
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            onClick={() => openEditModal(market)}
                            className="rounded-full border border-white/90 bg-white/75 px-3 py-1 text-xs font-semibold shadow-sm"
                          >
                            Editar
                          </button>
                          <button
                            type="button"
                            onClick={() => statusMutation.mutate({ marketId: market.id, active: !market.active })}
                            className="rounded-full border border-white/90 bg-white/75 px-3 py-1 text-xs font-semibold shadow-sm"
                          >
                            {market.active ? "Desactivar" : "Activar"}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <Modal
        open={isModalOpen}
        title={editingMarket ? "Editar Espacio" : "Nuevo Espacio"}
        description="Los cambios visibles hablan de Espacio, aunque la estructura interna siga usando Market por estabilidad."
        onClose={handleCloseModal}
      >
        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-2 text-sm">
              <span>Nombre</span>
              <input
                required
                value={formState.name}
                onChange={(event) => setFormState((current) => ({ ...current, name: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>
            <label className="grid gap-2 text-sm">
              <span>Correo de login</span>
              <input
                required
                type="email"
                value={formState.email}
                onChange={(event) => setFormState((current) => ({ ...current, email: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>
            <label className="grid gap-2 text-sm">
              <span>Telefono</span>
              <input
                value={formState.phone}
                onChange={(event) => setFormState((current) => ({ ...current, phone: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>
            <label className="grid gap-2 text-sm">
              <span>Nombre de contacto</span>
              <input
                value={formState.contactName}
                onChange={(event) => setFormState((current) => ({ ...current, contactName: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>
          </div>

          <label className="grid gap-2 text-sm">
            <span>Descripcion</span>
            <textarea
              rows={4}
              value={formState.description}
              onChange={(event) => setFormState((current) => ({ ...current, description: event.target.value }))}
              className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
            />
          </label>

          <label className="flex items-center gap-3 rounded-2xl border border-white/80 bg-[linear-gradient(135deg,rgba(255,246,250,0.96),rgba(244,239,255,0.94))] px-4 py-3 text-sm">
            <input
              type="checkbox"
              checked={formState.active}
              onChange={(event) => setFormState((current) => ({ ...current, active: event.target.checked }))}
            />
            Espacio activo
          </label>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
            >
              {editingMarket ? "Guardar cambios" : "Crear Espacio"}
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}

function MetricCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return (
    <div className="soft-surface p-6">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-3 text-3xl font-semibold tracking-tight">{value}</p>
      <p className="mt-2 text-sm text-muted-foreground">{helper}</p>
    </div>
  );
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
