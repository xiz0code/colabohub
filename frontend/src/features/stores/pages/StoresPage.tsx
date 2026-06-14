import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { listMarkets } from "@/features/markets/api/marketApi";
import {
  createStore,
  listStores,
  type Store,
  updateStore,
  updateStoreStatus,
} from "@/features/stores/api/storeApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type StoreFormState = {
  marketId: string;
  code: string;
  name: string;
  type: "PRIMARY" | "COLLABORATOR";
};

const initialFormState: StoreFormState = {
  marketId: "",
  code: "",
  name: "",
  type: "COLLABORATOR",
};

export function StoresPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"" | "ACTIVE" | "INACTIVE">("");
  const [editingStore, setEditingStore] = useState<Store | null>(null);
  const [formState, setFormState] = useState<StoreFormState>(initialFormState);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const marketsQuery = useQuery({
    queryKey: ["markets"],
    queryFn: listMarkets,
  });

  const storesQuery = useQuery({
    queryKey: ["stores", page, query, status],
    queryFn: () =>
      listStores({
        page,
        size: 8,
        query: query || undefined,
        status: status || undefined,
      }),
  });

  const storeRows = useMemo(() => storesQuery.data?.content ?? [], [storesQuery.data]);

  const createStoreMutation = useMutation({
    mutationFn: createStore,
    onSuccess: () => {
      setFeedback({ kind: "success", message: "Tienda creada correctamente." });
      setFormState(initialFormState);
      queryClient.invalidateQueries({ queryKey: ["stores"] });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible crear la tienda.") });
    },
  });

  const updateStoreMutation = useMutation({
    mutationFn: ({ storeId, input }: { storeId: number; input: StoreFormState }) =>
      updateStore(storeId, {
        marketId: Number(input.marketId),
        code: input.code,
        name: input.name,
        type: input.type,
      }),
    onSuccess: () => {
      setFeedback({ kind: "success", message: "Tienda actualizada correctamente." });
      resetForm();
      queryClient.invalidateQueries({ queryKey: ["stores"] });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar la tienda.") });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ storeId, nextStatus }: { storeId: number; nextStatus: Store["status"] }) =>
      updateStoreStatus(storeId, nextStatus),
    onSuccess: (_, variables) => {
      setFeedback({
        kind: "success",
        message: variables.nextStatus === "ACTIVE" ? "Tienda activada correctamente." : "Tienda desactivada correctamente.",
      });
      queryClient.invalidateQueries({ queryKey: ["stores"] });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cambiar el estado.") });
    },
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);

    const payload = {
      marketId: Number(formState.marketId),
      code: formState.code,
      name: formState.name,
      type: formState.type,
    };

    if (editingStore) {
      updateStoreMutation.mutate({ storeId: editingStore.id, input: formState });
      return;
    }

    createStoreMutation.mutate(payload);
  };

  const handleEdit = (store: Store) => {
    if (store.type === "STOCK") {
      setFeedback({ kind: "error", message: "Stock principal es una tienda tecnica y no se edita desde este formulario." });
      return;
    }

    setEditingStore(store);
    setFormState({
      marketId: String(store.marketId),
      code: store.code,
      name: store.name,
      type: store.type,
    });
    setFeedback(null);
  };

  const resetForm = () => {
    setEditingStore(null);
    setFormState(initialFormState);
  };

  return (
    <section>
      <PageHeader
        title="Tiendas"
        description="Administracion base de tiendas colaboradoras, ligadas a mercados y preparadas para el POS compartido."
      />

      <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <form onSubmit={handleSubmit} className="rounded-3xl border border-border/70 bg-card/80 p-6 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-semibold">{editingStore ? "Editar tienda" : "Nueva tienda"}</h2>
            {editingStore ? (
              <button type="button" onClick={resetForm} className="text-sm text-muted-foreground underline">
                Cancelar
              </button>
            ) : null}
          </div>

          <div className="mt-4 grid gap-4">
            <label className="grid gap-2 text-sm">
              <span>Mercado</span>
              <select
                required
                value={formState.marketId}
                onChange={(event) => setFormState((current) => ({ ...current, marketId: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2"
              >
                <option value="">Selecciona un mercado</option>
                {marketsQuery.data?.map((market) => (
                  <option key={market.id} value={market.id}>
                    {market.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="grid gap-2 text-sm">
              <span>Codigo</span>
              <input
                required
                value={formState.code}
                onChange={(event) => setFormState((current) => ({ ...current, code: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2"
              />
            </label>

            <label className="grid gap-2 text-sm">
              <span>Nombre</span>
              <input
                required
                value={formState.name}
                onChange={(event) => setFormState((current) => ({ ...current, name: event.target.value }))}
                className="rounded-2xl border border-input bg-background px-3 py-2"
              />
            </label>

            <label className="grid gap-2 text-sm">
              <span>Tipo</span>
              <select
                value={formState.type}
                onChange={(event) =>
                  setFormState((current) => ({ ...current, type: event.target.value as StoreFormState["type"] }))
                }
                className="rounded-2xl border border-input bg-background px-3 py-2"
              >
                <option value="COLLABORATOR">Colaboradora</option>
                <option value="PRIMARY">Principal</option>
              </select>
            </label>

            <button
              type="submit"
              disabled={createStoreMutation.isPending || updateStoreMutation.isPending}
              className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {editingStore
                ? updateStoreMutation.isPending
                  ? "Guardando..."
                  : "Guardar cambios"
                : createStoreMutation.isPending
                  ? "Creando..."
                  : "Crear tienda"}
            </button>
          </div>
        </form>

        <div className="space-y-4">
          {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

          <div className="rounded-3xl border border-border/70 bg-card/80 p-6 shadow-sm">
            <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <h2 className="text-lg font-semibold">Listado de tiendas</h2>
              <div className="flex flex-col gap-3 sm:flex-row">
                <input
                  placeholder="Buscar por codigo o nombre"
                  value={query}
                  onChange={(event) => {
                    setPage(0);
                    setQuery(event.target.value);
                  }}
                  className="rounded-2xl border border-input bg-background px-3 py-2 text-sm"
                />
                <select
                  value={status}
                  onChange={(event) => {
                    setPage(0);
                    setStatus(event.target.value as typeof status);
                  }}
                  className="rounded-2xl border border-input bg-background px-3 py-2 text-sm"
                >
                  <option value="">Todos los estados</option>
                  <option value="ACTIVE">Activas</option>
                  <option value="INACTIVE">Inactivas</option>
                </select>
              </div>
            </div>

            {storesQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando tiendas..." /> : null}
            {storesQuery.isError ? (
              <FeedbackMessage kind="error" message={getErrorMessage(storesQuery.error, "No fue posible cargar las tiendas.")} />
            ) : null}

            {storesQuery.data?.empty ? (
              <EmptyState
                title="No hay tiendas para mostrar"
                description="Crea una nueva tienda o ajusta los filtros para ver resultados."
              />
            ) : (
              <div className="overflow-hidden rounded-2xl border border-border/60">
                <table className="min-w-full divide-y divide-border/60 text-sm">
                  <thead className="bg-secondary/50 text-left">
                    <tr>
                      <th className="px-4 py-3 font-medium">Codigo</th>
                      <th className="px-4 py-3 font-medium">Nombre</th>
                      <th className="px-4 py-3 font-medium">Mercado</th>
                      <th className="px-4 py-3 font-medium">Tipo</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                      <th className="px-4 py-3 font-medium">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {storeRows.map((store) => (
                      <tr key={store.id}>
                        <td className="px-4 py-3">{store.code}</td>
                        <td className="px-4 py-3">{store.name}</td>
                        <td className="px-4 py-3">{store.marketName}</td>
                        <td className="px-4 py-3">{store.type}</td>
                        <td className="px-4 py-3">{store.status}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => handleEdit(store)}
                              disabled={store.type === "STOCK" || statusMutation.isPending || updateStoreMutation.isPending}
                              className="rounded-full border border-border px-3 py-1 text-xs"
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                statusMutation.mutate({
                                  storeId: store.id,
                                  nextStatus: store.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                                })
                              }
                              disabled={statusMutation.isPending}
                              className="rounded-full border border-border px-3 py-1 text-xs disabled:opacity-50"
                            >
                              {store.status === "ACTIVE" ? "Desactivar" : "Activar"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="mt-4 flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                {storesQuery.data ? `Pagina ${storesQuery.data.page + 1} de ${Math.max(storesQuery.data.totalPages, 1)}` : ""}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={storesQuery.data?.first ?? true}
                  onClick={() => setPage((current) => Math.max(current - 1, 0))}
                  className="rounded-full border border-border px-3 py-1 disabled:opacity-50"
                >
                  Anterior
                </button>
                <button
                  type="button"
                  disabled={storesQuery.data?.last ?? true}
                  onClick={() => setPage((current) => current + 1)}
                  className="rounded-full border border-border px-3 py-1 disabled:opacity-50"
                >
                  Siguiente
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
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
