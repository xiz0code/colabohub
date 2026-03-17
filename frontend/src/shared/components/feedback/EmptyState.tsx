type EmptyStateProps = {
  title: string;
  description: string;
};

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="soft-subtle-surface bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,248,252,0.93))] px-6 py-10 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-[22px] bg-[linear-gradient(135deg,rgba(255,221,236,0.92),rgba(234,228,255,0.9))] text-2xl font-semibold text-fuchsia-700 shadow-[0_16px_28px_rgba(189,169,219,0.18)]">
        *
      </div>
      <p className="mt-5 text-lg font-semibold tracking-tight">{title}</p>
      <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-muted-foreground">{description}</p>
    </div>
  );
}
