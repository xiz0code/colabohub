type StatCardProps = {
  label: string;
  value: string;
  helper: string;
};

export function StatCard({ label, value, helper }: StatCardProps) {
  return (
    <article className="soft-surface relative overflow-hidden p-5">
      <div className="absolute right-0 top-0 h-24 w-24 rounded-full bg-[radial-gradient(circle,rgba(232,223,255,0.9),transparent_60%)]" />
      <p className="relative text-sm font-medium text-muted-foreground">{label}</p>
      <p className="relative mt-3 text-3xl font-semibold tracking-tight">{value}</p>
      <p className="relative mt-2 text-sm leading-6 text-muted-foreground">{helper}</p>
    </article>
  );
}
