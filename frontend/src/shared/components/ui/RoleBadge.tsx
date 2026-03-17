import { getRoleBadgeClasses, getRoleBadgeDotClass, getRoleLabel } from "@/shared/lib/auth/roles";

type RoleBadgeProps = {
  role: string | null | undefined;
};

export function RoleBadge({ role }: RoleBadgeProps) {
  return (
    <span
      className={[
        "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold tracking-wide shadow-sm",
        getRoleBadgeClasses(role),
      ].join(" ")}
    >
      <span className={["h-2.5 w-2.5 rounded-full shadow-[0_0_0_3px_rgba(255,255,255,0.72)]", getRoleBadgeDotClass(role)].join(" ")} />
      {getRoleLabel(role)}
    </span>
  );
}
