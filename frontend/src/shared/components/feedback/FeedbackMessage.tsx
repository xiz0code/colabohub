type FeedbackMessageProps = {
  kind: "success" | "error" | "info";
  message: string;
};

export function FeedbackMessage({ kind, message }: FeedbackMessageProps) {
  const styleMap = {
    success:
      "border-emerald-200/90 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(233,252,242,0.95))] text-emerald-900",
    error:
      "border-rose-200/90 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(255,237,240,0.96))] text-rose-900",
    info:
      "border-violet-200/90 bg-[linear-gradient(135deg,rgba(255,255,255,0.98),rgba(244,239,255,0.96))] text-violet-900",
  } as const;

  const iconMap = {
    success: "OK",
    error: "!",
    info: "i",
  } as const;

  return (
    <div className={`flex items-start gap-3 rounded-[24px] border px-4 py-3.5 text-sm shadow-[0_16px_28px_rgba(181,169,206,0.08)] ${styleMap[kind]}`}>
      <span className="mt-0.5 inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-white/85 px-1 text-[11px] font-bold text-current shadow-sm">
        {iconMap[kind]}
      </span>
      <p className="leading-6">{message}</p>
    </div>
  );
}
