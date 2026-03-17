export type AppRole = "ADMIN_SYSTEM" | "ADMIN_MARKET" | "COLLABORATOR" | "STORE_USER";

const rolePriority: AppRole[] = ["ADMIN_SYSTEM", "ADMIN_MARKET", "STORE_USER", "COLLABORATOR"];

const roleLabels: Record<AppRole, string> = {
  ADMIN_SYSTEM: "Administrador General",
  ADMIN_MARKET: "Administrador de Tienda",
  STORE_USER: "Colaborador",
  COLLABORATOR: "Administrador de Tienda",
};

const roleBadgeClasses: Record<AppRole, string> = {
  ADMIN_SYSTEM:
    "border-fuchsia-200/90 bg-[linear-gradient(135deg,rgba(255,241,248,0.96),rgba(239,234,255,0.96))] text-fuchsia-800",
  ADMIN_MARKET:
    "border-emerald-200/90 bg-[linear-gradient(135deg,rgba(240,255,248,0.96),rgba(232,252,246,0.96))] text-emerald-800",
  STORE_USER:
    "border-sky-200/90 bg-[linear-gradient(135deg,rgba(240,249,255,0.96),rgba(236,244,255,0.96))] text-sky-800",
  COLLABORATOR:
    "border-violet-200/90 bg-[linear-gradient(135deg,rgba(247,242,255,0.96),rgba(240,236,255,0.96))] text-violet-800",
};

const roleBadgeDots: Record<AppRole, string> = {
  ADMIN_SYSTEM: "bg-fuchsia-400",
  ADMIN_MARKET: "bg-emerald-400",
  STORE_USER: "bg-sky-400",
  COLLABORATOR: "bg-violet-400",
};

export function getPrimaryRole(roles: string[]): AppRole | null {
  return rolePriority.find((role) => roles.includes(role)) ?? null;
}

export function getRoleLabel(role: string | null | undefined) {
  if (!role) {
    return "Usuario";
  }
  return roleLabels[role as AppRole] ?? role;
}

export function getRoleBadgeClasses(role: string | null | undefined) {
  if (!role) {
    return "border-border bg-secondary text-secondary-foreground";
  }
  return roleBadgeClasses[role as AppRole] ?? "border-border bg-secondary text-secondary-foreground";
}

export function getRoleBadgeDotClass(role: string | null | undefined) {
  if (!role) {
    return "bg-slate-300";
  }
  return roleBadgeDots[role as AppRole] ?? "bg-slate-300";
}
