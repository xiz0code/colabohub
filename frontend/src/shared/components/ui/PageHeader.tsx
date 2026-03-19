import { type ReactNode } from "react";

type PageHeaderProps = {
  title: string;
  description: string;
  eyebrow?: string;
  actions?: ReactNode;
};

export function PageHeader({ title, description, eyebrow = "Espacio ColaboHub", actions }: PageHeaderProps) {
  return (
    <div className="soft-surface relative mb-6 overflow-hidden p-6 md:p-7">
      <div className="absolute inset-y-0 right-0 hidden w-40 bg-[radial-gradient(circle_at_top,rgba(255,201,227,0.28),transparent_58%),radial-gradient(circle_at_bottom,rgba(177,205,255,0.24),transparent_54%)] md:block" />
      <div className="relative flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
        <div>
          <span className="soft-chip bg-[linear-gradient(135deg,rgba(255,255,255,0.95),rgba(255,239,247,0.96))] text-[11px] uppercase tracking-[0.16em] text-fuchsia-700">
            {eyebrow}
          </span>
          <h1 className="mt-4 text-3xl font-semibold tracking-tight text-foreground md:text-[2.1rem]">{title}</h1>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">{description}</p>
        </div>
        {actions ? <div className="md:self-center">{actions}</div> : null}
      </div>
    </div>
  );
}
