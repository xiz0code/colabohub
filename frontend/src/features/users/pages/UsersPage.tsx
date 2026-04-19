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

type UsersPageMode = "stores" | "sellers";

type UserFormState = {
  email: string;
  fullName: string;
  phone: string;
  contactName: string;
  description: string;
  role: "STORE_USER" | "SELLER";
  marketId: string;
  monthlyRent: string;
  startDate: string;
  standNumber: string;
  factura: boolean;
  active: boolean;
};

const EMPTY_STORE_FORM: UserFormState = {
  email: "",
  fullName: "",
  phone: "",
  contactName: "",
  description: "",
  role: "STORE_USER",
  marketId: "",
  monthlyRent: "",
  startDate: "",
  standNumber: "",
  factura: false,
  active: true,
};

const EMPTY_SELLER_FORM: UserFormState = {
  email: "",
  fullName: "",
  phone: "",
  contactName: "",
  description: "",
  role: "SELLER",
  marketId: "",
  monthlyRent: "",
  startDate: "",
  standNumber: "",
  factura: false,
  active: true,
};

export function UsersPage({ mode = "stores" }: { mode?: UsersPageMode }) {
  const { primaryRole } = useSession();
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AppUser | null>(null);
  const [formState, setFormState] = useState<UserFormState>(getInitialForm(mode));
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);

  const isSellerMode = mode === "sellers";
  const entityLabel = isSellerMode ? "vendedor" : "Tienda";
  const entityLabelPlural = isSellerMode ? "Vendedores" : "Tiendas";

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
      return isSellerMode ? role === "SELLER" : role === "STORE_USER";
    });
  }, [isSellerMode, usersQuery.data]);

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({
        kind: "success",
        message: isSellerMode ? "Vendedor creado correctamente." : "Tienda creada correctamente.",
      });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({
        kind: "error",
        message: getErrorMessage(
          error,
          isSellerMode ? "No fue posible crear el vendedor." : "No fue posible crear la Tienda.",
        ),
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ userId, input }: { userId: number; input: UpsertUserInput }) => updateUser(userId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({
        kind: "success",
        message: isSellerMode ? "Vendedor actualizado correctamente." : "Tienda actualizada correctamente.",
      });
      handleCloseModal();
    },
    onError: (error) => {
      setFeedback({
        kind: "error",
        message: getErrorMessage(
          error,
          isSellerMode ? "No fue posible actualizar el vendedor." : "No fue posible actualizar la Tienda.",
        ),
      });
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ userId, active }: { userId: number; active: boolean }) => updateUserStatus(userId, active),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["users"] });
      setFeedback({
        kind: "success",
        message: variables.active
          ? `${capitalize(entityLabel)} activ${isSellerMode ? "o" : "a"} correctamente.`
          : `${capitalize(entityLabel)} desactiv${isSellerMode ? "o" : "a"} correctamente.`,
      });
    },
    onError: (error) => {
      setFeedback({
        kind: "error",
        message: getErrorMessage(
          error,
          isSellerMode
            ? "No fue posible cambiar el estado del vendedor."
            : "No fue posible cambiar el estado de la Tienda.",
        ),
      });
    },
  });

  const openCreateModal = () => {
    setEditingUser(null);
    setFormState(getInitialForm(mode));
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
      marketId: user.marketIds[0] ? String(user.marketIds[0]) : "",
      role: isSellerMode ? "SELLER" : "STORE_USER",
      monthlyRent: user.monthlyRent != null ? String(user.monthlyRent) : "",
      startDate: user.startDate ?? "",
      standNumber: user.standNumber ?? "",
      factura: user.factura,
      active: user.active,
    });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingUser(null);
    setFormState(getInitialForm(mode));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload: UpsertUserInput = {
      email: formState.email,
      fullName: formState.fullName,
      phone: formState.phone || undefined,
      contactName: formState.contactName || undefined,
      description: formState.description || undefined,
      role: isSellerMode ? "SELLER" : "STORE_USER",
      marketIds: formState.marketId ? [Number(formState.marketId)] : undefined,
      active: formState.active,
      ...(isSellerMode
        ? {}
        : {
            monthlyRent: formState.monthlyRent ? Number(formState.monthlyRent) : undefined,
            startDate: formState.startDate || undefined,
            standNumber: formState.standNumber || undefined,
            factura: formState.factura,
          }),
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
        title={isSellerMode ? "Gestion de vendedores" : "Gestion de Tiendas"}
        description={
          isSellerMode
            ? "Administra el acceso de vendedores internos que operan la caja del Espacio."
            : "Administra las Tiendas internas de tu Espacio, sus datos comerciales y su acceso operativo."
        }
        eyebrow={primaryRole === "ADMIN_SYSTEM" ? "Vista global" : "Gestion de Espacio"}
      />

      <div className="space-y-6">
        {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

        <div className="soft-surface p-6">
          <div className="mb-5 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <h2 className="text-xl font-semibold">{isSellerMode ? "Equipo de caja" : "Tiendas del Espacio"}</h2>
              <p className="text-sm text-muted-foreground">
                {isSellerMode
                  ? "Los Administradores de Espacio pueden crear y mantener vendedores solo dentro del Espacio activo."
                  : "Cada Tienda opera dentro del Espacio asignado y puede quedar ligada a productos, ventas y cierres."}
              </p>
            </div>
            <button
              type="button"
              onClick={openCreateModal}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)]"
            >
              {isSellerMode ? "Nuevo vendedor" : "Nueva Tienda"}
            </button>
          </div>

          {usersQuery.isLoading ? <FeedbackMessage kind="info" message={`Cargando ${entityLabelPlural.toLowerCase()}...`} /> : null}
          {usersQuery.isError ? (
            <FeedbackMessage
              kind="error"
              message={getErrorMessage(
                usersQuery.error,
                isSellerMode ? "No fue posible cargar los vendedores." : "No fue posible cargar las Tiendas.",
              )}
            />
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && visibleUsers.length === 0 ? (
            <EmptyState
              title={isSellerMode ? "No hay vendedores" : "No hay Tiendas"}
              description={
                isSellerMode
                  ? "Crea el primer vendedor del Espacio para habilitar la operacion rapida de caja."
                  : "Crea la primera Tienda del Espacio para habilitar productos y operacion comercial."
              }
            />
          ) : (
            <div className="soft-table">
              <table>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    {isSellerMode ? <th>RUT</th> : null}
                    {isSellerMode ? <th>Telefono</th> : null}
                    <th>Correo</th>
                    <th>Rol</th>
                    <th>Espacio</th>
                    <th>Fecha creacion</th>
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
                            <p className="text-sm text-muted-foreground">
                              {user.description || (isSellerMode ? "Sin descripcion adicional." : user.contactName || "Sin detalle adicional.")}
                            </p>
                          </div>
                        </td>
                        {isSellerMode ? <td>{user.contactName || "Sin RUT"}</td> : null}
                        {isSellerMode ? <td>{user.phone || "Sin telefono"}</td> : null}
                        <td>{user.email}</td>
                        <td>
                          <RoleBadge role={role} />
                        </td>
                        <td>{marketNames || "Sin Espacio asignado"}</td>
                        <td>{formatDate(user.createdAt)}</td>
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
        title={
          editingUser
            ? isSellerMode
              ? "Editar vendedor"
              : "Editar Tienda"
            : isSellerMode
              ? "Nuevo vendedor"
              : "Nueva Tienda"
        }
        description={
          isSellerMode
            ? "Los vendedores usan Google para entrar y operar solo la caja del Espacio."
            : "Las Tiendas ingresan con Google usando su correo configurado y quedan con permiso interno STORE_USER."
        }
        onClose={handleCloseModal}
      >
        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="grid gap-2 text-sm">
              <span>{isSellerMode ? "Nombre del vendedor" : "Nombre de la Tienda"}</span>
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
              <span>{isSellerMode ? "RUT" : "Nombre de contacto"}</span>
              <input
                value={formState.contactName}
                onChange={(event) => setFormState((current) => ({ ...current, contactName: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              />
            </label>

            {!isSellerMode ? (
              <>
                <label className="grid gap-2 text-sm">
                  <span>Arriendo mensual</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={formState.monthlyRent}
                    onChange={(event) => setFormState((current) => ({ ...current, monthlyRent: event.target.value }))}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
                <label className="grid gap-2 text-sm">
                  <span>Fecha de inicio</span>
                  <input
                    type="date"
                    value={formState.startDate}
                    onChange={(event) => setFormState((current) => ({ ...current, startDate: event.target.value }))}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
                <label className="grid gap-2 text-sm md:col-span-2">
                  <span>Numero de stand</span>
                  <input
                    value={formState.standNumber}
                    onChange={(event) => setFormState((current) => ({ ...current, standNumber: event.target.value }))}
                    className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
                  />
                </label>
                <label className="flex items-center gap-3 rounded-2xl border border-white/80 bg-[linear-gradient(135deg,rgba(255,246,250,0.96),rgba(244,239,255,0.94))] px-4 py-3 text-sm md:col-span-2">
                  <input
                    type="checkbox"
                    checked={formState.factura}
                    onChange={(event) => setFormState((current) => ({ ...current, factura: event.target.checked }))}
                  />
                  Esta Tienda emite factura
                </label>
              </>
            ) : null}
          </div>

          {primaryRole === "ADMIN_SYSTEM" ? (
            <label className="grid gap-2 text-sm">
              <span>Espacio</span>
              <select
                required
                value={formState.marketId}
                onChange={(event) => setFormState((current) => ({ ...current, marketId: event.target.value }))}
                className="rounded-2xl border border-input bg-background/80 px-3 py-2.5"
              >
                <option value="">Selecciona un Espacio</option>
                {(marketsQuery.data ?? []).map((market) => (
                  <option key={market.id} value={String(market.id)}>
                    {market.name}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <div className="rounded-2xl border border-border/70 bg-background/70 px-4 py-3 text-sm text-muted-foreground">
              {isSellerMode
                ? "Este vendedor se asignara automaticamente al Espacio activo que administras."
                : "Esta Tienda se asignara automaticamente al Espacio activo que administras."}
            </div>
          )}

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
            {isSellerMode ? "Vendedor activo" : "Tienda activa"}
          </label>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] disabled:opacity-50"
            >
              {editingUser ? "Guardar cambios" : isSellerMode ? "Crear vendedor" : "Crear Tienda"}
            </button>
          </div>
        </form>
      </Modal>
    </section>
  );
}

function getInitialForm(mode: UsersPageMode): UserFormState {
  return mode === "sellers" ? { ...EMPTY_SELLER_FORM } : { ...EMPTY_STORE_FORM };
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
  }).format(new Date(value));
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}
