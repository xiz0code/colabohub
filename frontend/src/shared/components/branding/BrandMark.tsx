import { APP_BRANDING } from "@/shared/lib/branding";

export function BrandMark({
  className = "",
  size = "md",
}: {
  className?: string;
  size?: "sm" | "md" | "lg";
}) {
  const sizeClasses =
    size === "sm"
      ? "h-12 w-12 rounded-[18px] text-sm"
      : size === "lg"
        ? "h-20 w-20 rounded-[28px] text-2xl"
        : "h-14 w-14 rounded-[22px] text-base";

  if (APP_BRANDING.logoSrc) {
    return (
      <img
        src={APP_BRANDING.logoSrc}
        alt={APP_BRANDING.logoAlt}
        className={`${sizeClasses} object-contain ${className}`.trim()}
      />
    );
  }

  return (
    <div
      className={[
        "inline-flex items-center justify-center bg-[linear-gradient(135deg,rgba(255,211,229,0.96),rgba(201,212,255,0.98))] font-extrabold text-fuchsia-950 shadow-[0_16px_30px_rgba(188,161,219,0.22)]",
        sizeClasses,
        className,
      ].join(" ")}
      aria-label={APP_BRANDING.logoAlt}
    >
      Co
    </div>
  );
}
