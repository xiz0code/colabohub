import { type ReactNode, FormEvent, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  getGlobalFinancialSettings,
  getMarketFinancialSettings,
  resetMarketUfValueToAutomatic,
  updateGlobalCommissionSettings,
  updateGlobalUfValue,
  updateMarketCommissionSettings,
  updateMarketUfValue,
} from "@/features/commissions/api/settingsApi";
import { listMarkets } from "@/features/markets/api/marketApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

export function CommissionsPage() {
  const queryClient = useQueryClient();
  const { primaryRole, user } = useSession();
  const isSystemAdmin = primaryRole === "ADMIN_SYSTEM";
  const isMarketAdmin = primaryRole === "ADMIN_MARKET";
  const activeMarketId = isMarketAdmin ? (user?.activeMarketId ?? null) : null;
  const [selectedMarketId, setSelectedMarketId] = useState<string>("");
  const [globalForm, setGlobalForm] = useState({
    ufValue: "",
    commissionUfValue: "",
    commissionPercentageValue: "",
    useDynamicFixedCommission: true,
  });
  const [marketForm, setMarketForm] = useState({
    overrideEnabled: false,
    commissionUfValue: "",
    commissionPercentageValue: "",
    globalPromotionEnabled: false,
    globalPromotionPercentage: "",
    ufValue: "",
  });
  const [feedback, setFeedback] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [isEditingMarketUf, setIsEditingMarketUf] = useState(false);

  const marketsQuery = useQuery({
    queryKey: ["markets", "settings"],
    queryFn: listMarkets,
    enabled: isSystemAdmin,
  });

  useEffect(() => {
    if (activeMarketId) {
      setSelectedMarketId(String(activeMarketId));
      return;
    }

    if (isSystemAdmin && !selectedMarketId && marketsQuery.data?.length) {
      setSelectedMarketId(String(marketsQuery.data[0].id));
    }
  }, [activeMarketId, isSystemAdmin, marketsQuery.data, selectedMarketId]);

  const globalSettingsQuery = useQuery({
    queryKey: ["settings", "global"],
    queryFn: getGlobalFinancialSettings,
    enabled: isSystemAdmin,
  });

  const marketSettingsQuery = useQuery({
    queryKey: ["settings", "market", selectedMarketId],
    queryFn: () => getMarketFinancialSettings(Number(selectedMarketId)),
    enabled: Boolean(selectedMarketId),
  });

  useEffect(() => {
    if (!globalSettingsQuery.data) {
      return;
    }

    setGlobalForm({
      ufValue: String(globalSettingsQuery.data.currentUfValue),
      commissionUfValue: String(globalSettingsQuery.data.commissionUfValue),
      commissionPercentageValue: String(globalSettingsQuery.data.commissionPercentageValue),
      useDynamicFixedCommission: globalSettingsQuery.data.useDynamicFixedCommission,
    });
  }, [globalSettingsQuery.data]);

  useEffect(() => {
    if (!marketSettingsQuery.data) {
      return;
    }

    setMarketForm({
      overrideEnabled: marketSettingsQuery.data.overrideEnabled,
      commissionUfValue: String(marketSettingsQuery.data.effectiveCommissionUfValue),
      commissionPercentageValue: String(marketSettingsQuery.data.effectiveCommissionPercentageValue),
      globalPromotionEnabled: marketSettingsQuery.data.globalPromotionEnabled,
      globalPromotionPercentage: String(marketSettingsQuery.data.globalPromotionPercentage ?? ""),
      ufValue: String(marketSettingsQuery.data.ufValue ?? ""),
    });
    setIsEditingMarketUf(false);
  }, [marketSettingsQuery.data]);

  const updateUfMutation = useMutation({
    mutationFn: (ufValue: number) => updateGlobalUfValue(ufValue),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "UF global actualizada correctamente." });
      await queryClient.invalidateQueries({ queryKey: ["settings", "global"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar la UF global.") }),
  });

  const updateGlobalCommissionsMutation = useMutation({
    mutationFn: ({ commissionUfValue, commissionPercentageValue }: { commissionUfValue: number; commissionPercentageValue: number }) =>
      updateGlobalCommissionSettings(
        commissionUfValue,
        commissionPercentageValue,
        globalForm.useDynamicFixedCommission,
      ),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Comisiones globales actualizadas correctamente." });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["settings", "global"] }),
        queryClient.invalidateQueries({ queryKey: ["settings", "market"] }),
      ]);
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar las comisiones globales.") }),
  });

  const updateMarketCommissionsMutation = useMutation({
    mutationFn: ({
      marketId,
      overrideEnabled,
      commissionUfValue,
      commissionPercentageValue,
      globalPromotionEnabled,
      globalPromotionPercentage,
    }: {
      marketId: number;
      overrideEnabled: boolean;
      commissionUfValue: number;
      commissionPercentageValue: number;
      globalPromotionEnabled: boolean;
      globalPromotionPercentage: number | null;
    }) =>
      updateMarketCommissionSettings(
        marketId,
        overrideEnabled,
        commissionUfValue,
        commissionPercentageValue,
        globalPromotionEnabled,
        globalPromotionPercentage,
      ),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Configuracion de la Tienda actualizada correctamente." });
      await queryClient.invalidateQueries({ queryKey: ["settings", "market"] });
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar la configuracion de la Tienda.") }),
  });

  const updateMarketUfMutation = useMutation({
    mutationFn: (ufValue: number) => updateMarketUfValue(ufValue),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Valor UF de la Tienda actualizado correctamente." });
      setIsEditingMarketUf(false);
      await queryClient.invalidateQueries({ queryKey: ["settings", "market"] });
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible actualizar el valor UF de la Tienda.") }),
  });

  const resetMarketUfMutation = useMutation({
    mutationFn: resetMarketUfValueToAutomatic,
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "La UF de la Tienda volvió a sincronizarse automáticamente." });
      setIsEditingMarketUf(false);
      await queryClient.invalidateQueries({ queryKey: ["settings", "market"] });
    },
    onError: (error) =>
      setFeedback({ kind: "error", message: getErrorMessage(error, "No fue posible volver a la UF automatica.") }),
  });

  const selectedMarketName = useMemo(() => {
    if (marketSettingsQuery.data) {
      return marketSettingsQuery.data.marketName;
    }
    if (user?.activeMarketName) {
      return user.activeMarketName;
    }
    return "tu Espacio";
  }, [marketSettingsQuery.data, user?.activeMarketName]);

  const handleGlobalUfSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);
    updateUfMutation.mutate(Number(globalForm.ufValue));
  };

  const handleGlobalCommissionsSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);
    updateGlobalCommissionsMutation.mutate({
      commissionUfValue: Number(globalForm.commissionUfValue),
      commissionPercentageValue: Number(globalForm.commissionPercentageValue),
    });
  };

  const handleMarketCommissionsSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedMarketId) {
      return;
    }
    setFeedback(null);
    updateMarketCommissionsMutation.mutate({
      marketId: Number(selectedMarketId),
      overrideEnabled: marketForm.overrideEnabled,
      commissionUfValue: Number(marketForm.commissionUfValue),
      commissionPercentageValue: Number(marketForm.commissionPercentageValue),
      globalPromotionEnabled: marketForm.globalPromotionEnabled,
      globalPromotionPercentage: marketForm.globalPromotionEnabled ? Number(marketForm.globalPromotionPercentage) : null,
    });
  };

  const handleMarketUfSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);
    updateMarketUfMutation.mutate(Number(marketForm.ufValue));
  };

  return (
    <section className="space-y-6">
      <PageHeader
        title="Configuracion"
        description="Ajusta comisiones, promociones y el valor UF de tu Espacio desde un espacio amplio y ordenado."
        eyebrow="Parametros financieros"
      />

      {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

      <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
        {isSystemAdmin ? (
          <Card
            title="Configuracion general"
            description="Estos valores funcionan como base del sistema y se aplican cuando un Espacio no tiene un override propio."
            headerAction={
              globalSettingsQuery.data ? <span className="soft-chip">UF global: {formatMoney(globalSettingsQuery.data.currentUfValue)}</span> : null
            }
          >
            {globalSettingsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando configuracion global..." /> : null}
            {globalSettingsQuery.isError ? (
              <FeedbackMessage
                kind="error"
                message={getErrorMessage(globalSettingsQuery.error, "No fue posible cargar la configuracion global.")}
              />
            ) : null}

            <form onSubmit={handleGlobalCommissionsSubmit} className="grid gap-4">
              <div className="grid gap-4 md:grid-cols-2">
                <label className="grid gap-2 text-sm">
                  <span>Comision fija global</span>
                  <input
                    required
                    min="0"
                    step="0.00001"
                    type="number"
                    value={globalForm.commissionUfValue}
                    onChange={(event) => setGlobalForm((current) => ({ ...current, commissionUfValue: event.target.value }))}
                    className="rounded-2xl border border-input bg-background px-3 py-2"
                  />
                </label>

                <label className="grid gap-2 text-sm">
                  <span>Comision variable global</span>
                  <input
                    required
                    min="0"
                    step="0.0001"
                    type="number"
                    value={globalForm.commissionPercentageValue}
                    onChange={(event) => setGlobalForm((current) => ({ ...current, commissionPercentageValue: event.target.value }))}
                    className="rounded-2xl border border-input bg-background px-3 py-2"
                  />
                </label>
              </div>

              <label className="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/80 px-4 py-3 text-sm">
                <input
                  type="checkbox"
                  checked={globalForm.useDynamicFixedCommission}
                  onChange={(event) =>
                    setGlobalForm((current) => ({ ...current, useDynamicFixedCommission: event.target.checked }))
                  }
                />
                <span>Usar comision fija dinamica por Tienda</span>
              </label>

              <div className="flex flex-wrap gap-3">
                <button
                  type="submit"
                  disabled={updateGlobalCommissionsMutation.isPending || globalSettingsQuery.isLoading}
                  className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                >
                  {updateGlobalCommissionsMutation.isPending ? "Guardando..." : "Guardar comisiones globales"}
                </button>
              </div>
            </form>

            <form onSubmit={handleGlobalUfSubmit} className="soft-subtle-surface mt-4 grid gap-4 p-4">
              <div>
                <h3 className="text-sm font-semibold">UF global</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  La mantenemos disponible para valores historicos y configuracion central del sistema.
                </p>
              </div>

              <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
                <label className="grid gap-2 text-sm">
                  <span>Valor UF</span>
                  <input
                    required
                    min="0.01"
                    step="0.01"
                    type="number"
                    value={globalForm.ufValue}
                    onChange={(event) => setGlobalForm((current) => ({ ...current, ufValue: event.target.value }))}
                    className="rounded-2xl border border-input bg-background px-3 py-2"
                  />
                </label>

                <button
                  type="submit"
                  disabled={updateUfMutation.isPending || globalSettingsQuery.isLoading}
                  className="rounded-2xl border border-border/70 bg-background px-4 py-3 text-sm font-semibold disabled:opacity-50"
                >
                  {updateUfMutation.isPending ? "Guardando..." : "Guardar UF global"}
                </button>
              </div>
            </form>
          </Card>
        ) : null}

        <Card
          title="Configuracion del Espacio"
          description="Revisa rapidamente el contexto activo antes de ajustar comisiones, promociones o UF."
          headerAction={
            isSystemAdmin ? (
              <select
                value={selectedMarketId}
                onChange={(event) => setSelectedMarketId(event.target.value)}
                className="rounded-2xl border border-input bg-background px-3 py-2 text-sm"
              >
                {marketsQuery.data?.map((market) => (
                  <option key={market.id} value={market.id}>
                    {market.name}
                  </option>
                ))}
              </select>
            ) : null
          }
        >
          {marketSettingsQuery.isLoading ? <FeedbackMessage kind="info" message="Cargando configuracion del Espacio..." /> : null}
          {marketSettingsQuery.isError ? (
            <FeedbackMessage
              kind="error"
              message={getErrorMessage(marketSettingsQuery.error, "No fue posible cargar la configuracion del Espacio.")}
            />
          ) : null}

          {!selectedMarketId && !marketSettingsQuery.isLoading ? (
            <EmptyState title="Selecciona un Espacio" description="Elige un Espacio para revisar su configuracion financiera." />
          ) : null}

          {marketSettingsQuery.data ? (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <SummaryPill label="Espacio" value={marketSettingsQuery.data.marketName} />
              <SummaryPill
                label="UF vigente"
                value={marketSettingsQuery.data.ufValue != null ? formatMoney(marketSettingsQuery.data.ufValue) : "Pendiente"}
              />
              <SummaryPill
                label="Modo UF"
                value={marketSettingsQuery.data.ufManualOverride ? "Manual" : "Automatico"}
              />
              <SummaryPill
                label="Comision fija efectiva"
                value={String(marketSettingsQuery.data.effectiveCommissionUfValue)}
              />
              <SummaryPill
                label="Comision variable efectiva"
                value={String(marketSettingsQuery.data.effectiveCommissionPercentageValue)}
              />
              <SummaryPill
                label="Modo comision fija"
                value={marketSettingsQuery.data.useDynamicFixedCommission ? "Dinamica por Tienda" : "Distribucion general"}
              />
            </div>
          ) : null}
        </Card>

        {marketSettingsQuery.data ? (
          <>
            <Card
              title="Comisiones"
              description={`Define si ${selectedMarketName} usara los valores globales o un override propio para las ventas futuras.`}
            >
              <form onSubmit={handleMarketCommissionsSubmit} className="grid gap-4">
                <label className="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/80 px-4 py-3 text-sm">
                  <input
                    type="checkbox"
                    checked={marketForm.overrideEnabled}
                    onChange={(event) => setMarketForm((current) => ({ ...current, overrideEnabled: event.target.checked }))}
                  />
                  <span>Activar override para este Espacio</span>
                </label>

                <div className="grid gap-4 md:grid-cols-2">
                  <label className="grid gap-2 text-sm">
                    <span>Comision fija</span>
                    <input
                      required
                      min="0"
                      step="0.00001"
                      type="number"
                      value={marketForm.commissionUfValue}
                      disabled={!marketForm.overrideEnabled}
                      onChange={(event) => setMarketForm((current) => ({ ...current, commissionUfValue: event.target.value }))}
                      className="rounded-2xl border border-input bg-background px-3 py-2 disabled:opacity-60"
                    />
                  </label>

                  <label className="grid gap-2 text-sm">
                    <span>Comision variable</span>
                    <input
                      required
                      min="0"
                      step="0.0001"
                      type="number"
                      value={marketForm.commissionPercentageValue}
                      disabled={!marketForm.overrideEnabled}
                      onChange={(event) => setMarketForm((current) => ({ ...current, commissionPercentageValue: event.target.value }))}
                      className="rounded-2xl border border-input bg-background px-3 py-2 disabled:opacity-60"
                    />
                  </label>
                </div>

                <div className="flex flex-wrap gap-3">
                  <button
                    type="submit"
                    disabled={updateMarketCommissionsMutation.isPending}
                    className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    {updateMarketCommissionsMutation.isPending ? "Guardando..." : "Guardar comisiones"}
                  </button>
                </div>
              </form>
            </Card>

            <Card
              title="Promocion global"
              description="Cuando esta activa, reemplaza temporalmente las promociones individuales de los productos del Espacio."
            >
              <form onSubmit={handleMarketCommissionsSubmit} className="grid gap-4">
                <label className="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/80 px-4 py-3 text-sm">
                  <input
                    type="checkbox"
                    checked={marketForm.globalPromotionEnabled}
                    onChange={(event) =>
                      setMarketForm((current) => ({
                        ...current,
                        globalPromotionEnabled: event.target.checked,
                        globalPromotionPercentage: event.target.checked ? current.globalPromotionPercentage : "",
                      }))
                    }
                  />
                  <span>Activar promocion global</span>
                </label>

                <label className="grid gap-2 text-sm md:max-w-xs">
                  <span>Descuento %</span>
                  <input
                    min="0"
                    max="100"
                    step="0.01"
                    type="number"
                    value={marketForm.globalPromotionPercentage}
                    disabled={!marketForm.globalPromotionEnabled}
                    onChange={(event) => setMarketForm((current) => ({ ...current, globalPromotionPercentage: event.target.value }))}
                    className="rounded-2xl border border-input bg-background px-3 py-2 disabled:opacity-60"
                  />
                </label>

                <div className="flex flex-wrap gap-3">
                  <button
                    type="submit"
                    disabled={updateMarketCommissionsMutation.isPending}
                    className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    {updateMarketCommissionsMutation.isPending ? "Guardando..." : "Guardar promocion global"}
                  </button>
                </div>
              </form>
            </Card>

            <Card
              title="Valor UF del Espacio"
              description="Cada venta nueva guarda este valor como snapshot. Puedes dejarlo automatico desde mindicador.cl o usar un override manual."
            >
              <form onSubmit={handleMarketUfSubmit} className="grid gap-4">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="soft-chip">
                    Estado: {marketSettingsQuery.data.ufManualOverride ? "Manual" : "Automatico"}
                  </span>
                  <span className="text-sm text-muted-foreground">
                    {marketSettingsQuery.data.ufUpdatedAt
                      ? `Ultima actualizacion: ${formatDateTime(marketSettingsQuery.data.ufUpdatedAt)}`
                      : "Aun no se ha definido una UF para este Espacio."}
                  </span>
                </div>

                <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
                  <label className="grid gap-2 text-sm">
                    <span>Valor UF</span>
                    <input
                      required
                      min="0.01"
                      step="0.01"
                      type="number"
                      value={marketForm.ufValue}
                      disabled={!isMarketAdmin || !isEditingMarketUf}
                      onChange={(event) => setMarketForm((current) => ({ ...current, ufValue: event.target.value }))}
                      className="rounded-2xl border border-input bg-background px-3 py-2 disabled:opacity-60"
                    />
                  </label>

                  <div className="flex flex-col gap-2 md:items-end">
                    {isMarketAdmin ? (
                      <div className="flex flex-wrap gap-2">
                        {!isEditingMarketUf ? (
                          <button
                            type="button"
                            onClick={() => setIsEditingMarketUf(true)}
                            className="rounded-2xl border border-border/70 bg-background px-4 py-3 text-sm font-semibold"
                          >
                            Editar UF
                          </button>
                        ) : (
                          <button
                            type="submit"
                            disabled={updateMarketUfMutation.isPending}
                            className="rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                          >
                            {updateMarketUfMutation.isPending ? "Guardando..." : "Guardar UF"}
                          </button>
                        )}

                        {marketSettingsQuery.data.ufManualOverride ? (
                          <button
                            type="button"
                            disabled={resetMarketUfMutation.isPending}
                            onClick={() => {
                              setFeedback(null);
                              resetMarketUfMutation.mutate();
                            }}
                            className="rounded-2xl border border-border/70 bg-background px-4 py-3 text-sm font-semibold disabled:opacity-50"
                          >
                            {resetMarketUfMutation.isPending ? "Actualizando..." : "Volver a automatico"}
                          </button>
                        ) : null}
                      </div>
                    ) : (
                      <span className="soft-chip">La UF se administra desde el Espacio activo.</span>
                    )}
                  </div>
                </div>
              </form>
            </Card>
          </>
        ) : null}
      </div>
    </section>
  );
}

function Card({
  title,
  description,
  headerAction,
  children,
}: {
  title: string;
  description: string;
  headerAction?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="soft-surface p-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">{title}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        </div>
        {headerAction ? <div className="shrink-0">{headerAction}</div> : null}
      </div>
      <div className="mt-5">{children}</div>
    </div>
  );
}

function SummaryPill({ label, value }: { label: string; value: string }) {
  return (
    <div className="soft-subtle-surface rounded-[22px] p-4">
      <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-base font-semibold text-foreground">{value}</p>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-CL", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(value);
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
