import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import { listMarkets } from "@/features/markets/api/marketApi";
import {
  createUser,
  listUsers,
  type AppUser,
  type UpsertUserInput,
  updateUser,
  updateUserStatus,
} from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { RoleBadge } from "@/shared/components/ui/RoleBadge";
import { getPrimaryRole } from "@/shared/lib/auth/roles";
import { ApiError } from "@/shared/lib/api/client";

type UserFormState = {
  email: string;
  fullName: string;
  phone: string;
  contactName: string;
  description: string;
  marketIds: string[];
  active: boolean;
};

const initialFormState: UserFormState = {
  email: "",
  fullName: "",
  phone: "",
  contactName: "",
  description: "",
  marketIds: [],
  active: true,
};

export function UsersPage() {
  const { primaryRole } = useSession();
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AppUser | null>(null);
  const [formState, setFormState] = useState<UserFormState>(initialFormState);
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const marketsQuery = useQuery({
    queryKey: ["markets"],
    queryFn: listMarkets,
  });

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: listUsers,
  });

  const visibleUsers = useMemo(() => {
    if (!usersQuery.data) {
      return [];
    }

    return usersQuery.data.filter((user) => {
      const role = getPrimaryRole(user.roles);
      return role === "STORE_USER" || primaryRole === "ADMIN_SYSTEM";
    });
  }, [primaryRole, usersQuery.data]);

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({ kind: "success", message: "Colaborador creado correctamente." });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible crear el colaborador.") });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ userId, input }: { userId: number; input: UpsertUserInput }) => updateUser(userId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({ kind: "success", message: "Colaborador actualizado correctamente." });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar el colaborador.") });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ userId, active }: { userId: number; active: boolean }) => updateUserStatus(userId, active),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({
        kind: "success",
        message: variables.active ? "Colaborador activado correctamente." : "Colaborador desactivado correctamente.",
      });
    },
    onError: (error) => {
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible cambiar el estado del colaborador.") });
    },
  });

  const openCreateModal = () => {
    setEditingUser(null);
    setFormState(initialFormState);
    setIsModalOpen(true);
  };

  const openEditModal = (user: AppUser) => {
    setEditingUser(user);
    setFormState({
      email: user.email,
      fullName: user.fullName,
      phone: user.phone ?? "",
      contactName: user.contactName ?? "",
      description: user.description ?? "",
      marketIds: user.marketIds.map(String),
      active: user.active,
    });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingUser(null);
    setFormState(initialFormState);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload: UpsertUserInput = {
      email: formState.email,
      fullName: formState.fullName,
      phone: formState.phone || undefined,
      contactName: formState.contactName || undefined,
      description: formState.description || undefined,
      role: "STORE_USER",
      marketIds: formState.marketIds.map(Number),
      active: formState.active,
    };

    if (editingUser) {
      updateMutation.mutate({ userId: editingUser.id, input: payload });
      return;
    }

    createMutation.mutate(payload);
  };

  return (
    <section>
      <PageHeader
        title="Colaboradores"
        description="Gestiona usuarios de lectura restringida asociados a Tiendas. No pueden vender, ajustar stock ni administrar el sistema."
        eyebrow={primaryRole === "ADMIN_SYSTEM" ? "Vista global" : "Gestion de Tienda"}
      />

      <div className="space-y-6">
        {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

        <div className="soft-surface p-6">
          <div className="mb-5 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Equipo operativo</h2>
              <p className="text-sm text-muted-foreground">
                Los Administradores de Tienda pueden manejar colaboradores solo dentro de su alcance asignado.
              </p>
            </div>
            <button
              type="button"
              onClick={openCreateModal}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)]"
            >
              Nuevo colaborador
            </button>
          </div>

          {usersQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando colaboradores..." /> : null}
          {usersQuery.isError ? (
            <FeedbackMessage kind="error" message={getErrorMessage(usersQuery.error, "No fue posible cargar los colaboradores.")} />
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && visibleUsers.length === 0 ? (
            <EmptyState
              title="No hay colaboradores"
              description="Crea el primer colaborador de una Tienda para habilitar su acceso de lectura."
            />
          ) : (
            <div className="soft-table">
              <table>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Correo</th>
                    <th>Rol</th>
                    <th>Tiendas asignadas</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleUsers.map((user) => {
                    const role = getPrimaryRole(user.roles);
                    const marketNames = (marketsQuery.data ?? [])
                      .filter((market) => user.marketIds.includes(market.id))
                      .map((market) => market.name)
                      .join(", ");

                    return (
                      <tr key={user.id}>
                        <td>
                          <div>
                            <p className="font-semibold">{user.fullName}</p>
                            <p className="text-sm text-muted-foreground">{user.contactName || user.description || "Sin detalle adicional."}</p>
                          </div>
                        </td>
                        <td>{user.email}</td>
                        <td>
                          <RoleBadge role={role} />
                        </td>
                        <td>{marketNames || "Sin Tienda asignada"}</td>
                        <td>
                          <span
                            className={[
                              "inline-flex rounded-full border px-3 py-1 text-xs font-semibold shadow-sm",
                              user.active
                                ? "border-emerald-200/90 bg-emerald-50/90 text-emerald-800"
                                : "border-slate-200/90 bg-slate-100/90 text-slate-700",
                            ].join(" ")}
                          >
                            {user.active ? "Activo" : "Inactivo"}
                          </span>
                        </td>
                        <td>
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              onClick={() => openEditModal(user)}
                              className="rounded-full border border-white/90 bg-white/75 px-3 py-1 text-xs font-semibold shadow-sm"
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              onClick={() => statusMutation.mutate({ userId: user.id, active: !user.active })}
                              className="rounded-full border border-white/90 bg-white/75 px-3 py-1 text-xs font-semibold shadow-sm"
                            >
                              {user.active ? "Desactivar" : "Activar"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <Modal
        open={isModalOpen}
        title={editingUser ? "Editar colaborador" : "Nuevo colaborador"}
        description="Los colaboradores ingresan con Google usando su correo configurado y quedan con permiso interno STORE_USER."
        onClose={handleCloseModal}
      >
        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-2 text-sm">
              <span>Nombre</span>
              <input
                required
                value={formState.fullName}
                onChange={(event) => setFormState((current) => ({ ...current, fullName: event.target.value }))}
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
            <span>Tiendas asignadas</span>
            <select
              multiple
              value={formState.marketIds}
              onChange={(event) =>
                setFormState((current) => ({
                  ...current,
                  marketIds: Array.from(event.target.selectedOptions, (option) => option.value),
                }))
              }
              className="min-h-32 rounded-2xl border border-input bg-background/80 px-3 py-2.5"
            >
              {(marketsQuery.data ?? []).map((market) => (
                <option key={market.id} value={String(market.id)}>
                  {market.name}
                </option>
              ))}
            </select>
          </label>

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
            Colaborador activo
          </label>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
            >
              {editingUser ? "Guardar cambios" : "Crear colaborador"}
            </button>
          </div>
        </form>
      </Modal>
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
