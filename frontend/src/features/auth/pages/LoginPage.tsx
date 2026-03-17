import { PageHeader } from "@/shared/components/ui/PageHeader";

import { useSession } from "@/features/auth/session/SessionProvider";

export function LoginPage() {
  const { loginUrl, isLoading } = useSession();

  return (
    <section className="mx-auto max-w-xl rounded-3xl border border-border/70 bg-card/80 p-8 shadow-sm">
      <PageHeader
        title="Acceso a ColaboHub"
        description="Inicia sesión con Google usando el correo registrado para tu tienda o tu cuenta interna."
      />

      <div className="mt-6 space-y-4">
        <a
          href={loginUrl}
          className="inline-flex w-full items-center justify-center rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground shadow-sm transition hover:opacity-90"
        >
          {isLoading ? "Verificando sesion..." : "Entrar con Google"}
        </a>

        <p className="text-sm text-muted-foreground">
          Si aún no puedes entrar, solicita al administrador que confirme el correo de acceso de tu tienda o tu usuario.
        </p>
      </div>
    </section>
  );
}
