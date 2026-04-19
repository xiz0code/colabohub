import { BrandMark } from "@/shared/components/branding/BrandMark";
import { APP_BRANDING } from "@/shared/lib/branding";

export function BrandLockup({
  className = "",
  size = "md",
}: {
  className?: string;
  size?: "sm" | "md" | "lg";
}) {
  const containerClasses =
    size === "sm"
      ? "gap-3"
      : size === "lg"
        ? "gap-5"
        : "gap-4";

  const nameClasses =
    size === "sm"
      ? "text-[1.55rem]"
      : size === "lg"
        ? "text-5xl"
        : "text-[2rem]";

  const subtitleClasses =
    size === "sm"
      ? "text-[0.72rem]"
      : size === "lg"
        ? "text-sm"
        : "text-xs";

  return (
    <div className={["inline-flex items-center", containerClasses, className].join(" ")} aria-label={APP_BRANDING.logoAlt}>
      <BrandMark size={size === "lg" ? "lg" : size === "sm" ? "sm" : "md"} />
      <div className="min-w-0">
        <div className={["font-black tracking-tight text-slate-950", nameClasses].join(" ")}>{APP_BRANDING.name}</div>
        <div className={["text-slate-500", subtitleClasses].join(" ")}>Gestion de Espacios Colaborativos</div>
      </div>
    </div>
  );
}
