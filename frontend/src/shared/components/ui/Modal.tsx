import { type PropsWithChildren, type ReactNode, useEffect } from "react";

type ModalProps = PropsWithChildren<{
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  footer?: ReactNode;
  maxWidthClassName?: string;
  closeOnOverlayClick?: boolean;
  closeOnEscape?: boolean;
}>;

export function Modal({
  open,
  title,
  description,
  onClose,
  footer,
  children,
  maxWidthClassName = "max-w-2xl",
  closeOnOverlayClick = true,
  closeOnEscape = true,
}: ModalProps) {
  useEffect(() => {
    if (!open || !closeOnEscape) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [closeOnEscape, onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[radial-gradient(circle_at_top,rgba(248,213,231,0.24),transparent_42%),rgba(74,58,103,0.24)] px-4 py-8 backdrop-blur-md"
      onMouseDown={() => {
        if (closeOnOverlayClick) {
          onClose();
        }
      }}
    >
      <div
        className={`w-full ${maxWidthClassName} overflow-hidden rounded-[34px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(255,248,252,0.97))] shadow-[0_40px_90px_rgba(112,89,150,0.2)]`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-border/60 bg-[linear-gradient(135deg,rgba(255,243,248,0.92),rgba(244,239,255,0.9))] px-6 py-5">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
            {description ? <p className="mt-1 max-w-xl text-sm leading-6 text-muted-foreground">{description}</p> : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-white/90 bg-white/80 px-3 py-1 text-sm font-medium transition hover:-translate-y-0.5 hover:bg-white"
          >
            Cerrar
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
        {footer ? <div className="border-t border-border/60 bg-background/70 px-6 py-4">{footer}</div> : null}
      </div>
    </div>
  );
}
