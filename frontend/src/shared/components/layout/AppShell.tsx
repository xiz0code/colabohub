import { Link, NavLink, Outlet } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import { RoleBadge } from "@/shared/components/ui/RoleBadge";
import { getNavigationItems } from "@/shared/lib/auth/navigation";

export function AppShell() {
  const { user, isLoading, logoutUrl, primaryRole, visibleRoleLabel } = useSession();
  const canOperatePos = primaryRole === "ADMIN_MARKET";
  const visibleNavItems = getNavigationItems(primaryRole);
  const marketSummary =
    primaryRole === "ADMIN_SYSTEM"
      ? "Acceso global"
      : user?.activeMarketName
        ? user.activeMarketName
      : user?.marketIds?.length
        ? user.marketIds.length === 1
          ? "Tu Tienda activa"
          : `${user.marketIds.length} Tiendas asignadas`
        : "Sin Tienda asignada";

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-white/80 bg-background/80 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-5 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:gap-5">
              <Link to="/dashboard" className="flex items-center gap-3">
                <span className="inline-flex h-12 w-12 items-center justify-center rounded-[20px] bg-[linear-gradient(135deg,rgba(255,211,229,0.96),rgba(201,212,255,0.98))] text-sm font-extrabold text-fuchsia-950 shadow-[0_16px_30px_rgba(188,161,219,0.22)]">
                  Co
                </span>
                <div>
                  <p className="text-lg font-semibold tracking-tight">ColaboHub</p>
                  <p className="text-sm text-muted-foreground">La plataforma suave para Tiendas creativas colaborativas</p>
                </div>
              </Link>

              {canOperatePos ? (
                <Link
                  to="/sales"
                  className="inline-flex items-center justify-center rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96),rgba(255,204,181,0.92))] px-6 py-3 text-base font-semibold text-white shadow-[0_16px_32px_rgba(186,153,228,0.26)] transition duration-200 hover:scale-[1.03] hover:-translate-y-0.5"
                >
                  Nueva venta
                </Link>
              ) : null}
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
              <div className="soft-surface flex items-center gap-4 px-4 py-3 sm:min-w-[320px]">
                <div className="hidden h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[linear-gradient(135deg,rgba(233,224,255,1),rgba(255,233,244,1))] text-sm font-bold text-violet-900 shadow-[0_12px_22px_rgba(179,160,216,0.2)] sm:inline-flex">
                  {(user?.fullName ?? user?.email ?? "U").slice(0, 1).toUpperCase()}
                </div>

                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{user?.fullName ?? user?.email ?? "Sesion activa"}</p>
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <span className="soft-chip">{marketSummary}</span>
                    <span className="soft-chip bg-[linear-gradient(135deg,rgba(255,248,227,0.96),rgba(255,255,255,0.92))] text-amber-700">
                      {visibleRoleLabel}
                    </span>
                  </div>
                </div>

                <div className="hidden shrink-0 sm:block">
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

      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="min-w-0">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
