import { Link, NavLink, Outlet } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import { PosLauncherProvider, usePosLauncher } from "@/features/sales/components/PosLauncherProvider";
import { BrandLockup } from "@/shared/components/branding/BrandLockup";
import { RoleBadge } from "@/shared/components/ui/RoleBadge";
import { getNavigationItems } from "@/shared/lib/auth/navigation";
import { APP_BRANDING } from "@/shared/lib/branding";

export function AppShell() {
  return (
    <PosLauncherProvider>
      <AppShellFrame />
    </PosLauncherProvider>
  );
}

function AppShellFrame() {
  const { user, isLoading, logoutUrl, primaryRole } = useSession();
  const { openPos, isOpening } = usePosLauncher();
  const canOperatePos = primaryRole === "ADMIN_MARKET" || primaryRole === "SELLER";
  const contextualCta: { to: string; label: string } | { action: "pos"; label: string } | null =
    primaryRole === "ADMIN_SYSTEM"
      ? { to: "/tiendas", label: "Gestionar Espacios" }
      : canOperatePos
        ? { action: "pos" as const, label: "Nueva venta" }
        : null;
  const visibleNavItems = getNavigationItems(primaryRole);
  const marketSummary =
    primaryRole === "ADMIN_SYSTEM"
      ? "Acceso global"
      : user?.activeMarketName
        ? user.activeMarketName
        : "Sin Espacio asignado";
  const profilePrimary =
    primaryRole === "ADMIN_SYSTEM"
      ? marketSummary
      : user?.fullName ?? user?.email ?? "Sesion activa";
  const profileSecondary =
    primaryRole === "ADMIN_SYSTEM"
      ? user?.fullName ?? user?.email ?? "Sesion activa"
      : marketSummary;

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-white/80 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-5 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:gap-5">
              <Link to="/dashboard" className="flex items-center gap-5">
                <BrandLockup size="sm" className="shrink-0" />
              </Link>

              {contextualCta ? (
                "to" in contextualCta ? (
                  <Link
                    to={contextualCta.to}
                    className="inline-flex min-h-14 items-center justify-center rounded-[24px] bg-[linear-gradient(135deg,rgba(177,146,239,1),rgba(242,157,206,0.98),rgba(255,196,170,0.96))] px-7 py-3 text-base font-bold text-white shadow-[0_18px_34px_rgba(184,150,228,0.3)] transition duration-200 hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-[0_20px_38px_rgba(184,150,228,0.36)]"
                  >
                    {contextualCta.label}
                  </Link>
                ) : (
                  <button
                    type="button"
                    onClick={openPos}
                    disabled={isOpening}
                    className="inline-flex min-h-14 items-center justify-center rounded-[24px] bg-[linear-gradient(135deg,rgba(177,146,239,1),rgba(242,157,206,0.98),rgba(255,196,170,0.96))] px-7 py-3 text-base font-bold text-white shadow-[0_18px_34px_rgba(184,150,228,0.3)] transition duration-200 hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-[0_20px_38px_rgba(184,150,228,0.36)] disabled:opacity-60"
                  >
                    {isOpening ? "Preparando venta..." : contextualCta.label}
                  </button>
                )
              ) : null}
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
              <div className="soft-surface flex items-center gap-4 px-4 py-3 sm:min-w-[320px]">
                <div className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[linear-gradient(135deg,rgba(233,224,255,1),rgba(255,233,244,1))] text-sm font-bold text-violet-900 shadow-[0_12px_22px_rgba(179,160,216,0.2)] sm:inline-flex">
                  {(user?.fullName ?? user?.email ?? "U").slice(0, 1).toUpperCase()}
                </div>

                <div className="min-w-0 flex-1">
                  <p className="truncate text-[1.35rem] font-semibold tracking-tight text-slate-900">{profilePrimary}</p>
                  <p className="mt-1 truncate text-sm font-medium text-slate-600">{profileSecondary}</p>
                </div>

                <div className="hidden self-center sm:block shrink-0">
                  <RoleBadge role={primaryRole} />
                </div>
              </div>

              <a
                href={logoutUrl}
                className="inline-flex h-11 items-center justify-center rounded-full border border-white/90 bg-white/75 px-4 text-sm font-semibold text-muted-foreground shadow-sm transition hover:-translate-y-0.5 hover:bg-white hover:text-foreground"
              >
                Salir
              </a>
            </div>
          </div>

          <nav
            className="flex flex-wrap gap-2 rounded-[24px] border border-white/80 bg-white/60 p-2 shadow-[0_12px_30px_rgba(182,170,208,0.08)]"
            aria-label="Navegacion principal"
          >
            {visibleNavItems.length > 0 ? (
              visibleNavItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    [
                      "rounded-full px-4 py-2 text-sm font-medium transition",
                      isActive
                        ? "bg-[linear-gradient(135deg,rgba(194,166,246,1),rgba(249,188,219,0.96))] text-white shadow-[0_10px_24px_rgba(187,156,232,0.22)]"
                        : "text-muted-foreground hover:bg-[linear-gradient(135deg,rgba(255,240,247,0.96),rgba(241,240,255,0.96))] hover:text-foreground",
                    ].join(" ")
                  }
                >
                  {item.label}
                </NavLink>
              ))
            ) : isLoading ? (
              <>
                <span className="h-10 w-28 animate-pulse rounded-full bg-[linear-gradient(135deg,rgba(255,240,247,0.96),rgba(241,240,255,0.96))]" />
                <span className="h-10 w-24 animate-pulse rounded-full bg-[linear-gradient(135deg,rgba(255,240,247,0.96),rgba(241,240,255,0.96))]" />
                <span className="h-10 w-32 animate-pulse rounded-full bg-[linear-gradient(135deg,rgba(255,240,247,0.96),rgba(241,240,255,0.96))]" />
              </>
            ) : null}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        <div className="min-w-0">
          <Outlet />
        </div>
      </main>

      <footer className="border-t border-white/60 bg-background/40">
        <div className="mx-auto max-w-7xl px-4 py-5 text-center text-xs text-slate-500 sm:px-6 lg:px-8">
          <span>&copy; 2026 {APP_BRANDING.name} · Powered by Xizo Dev&apos;s</span>
        </div>
      </footer>
    </div>
  );
}
