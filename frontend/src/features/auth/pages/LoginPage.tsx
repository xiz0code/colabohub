import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { useSession } from "@/features/auth/session/SessionProvider";
import { BrandLockup } from "@/shared/components/branding/BrandLockup";
import { APP_BRANDING } from "@/shared/lib/branding";

export function LoginPage() {
  const { loginUrl, isLoading } = useSession();
  const [searchParams] = useSearchParams();
  const accessError = useMemo(() => {
    const raw = searchParams.get("error");
    if (!raw) {
      return null;
    }
    return raw.trim();
  }, [searchParams]);

  return (
    <section className="mx-auto flex min-h-[calc(100vh-11rem)] w-full max-w-6xl items-center justify-center px-4 py-8 sm:px-6">
      <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="soft-surface relative overflow-hidden p-8 sm:p-10">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(255,223,238,0.55),transparent_32%),radial-gradient(circle_at_bottom_right,rgba(212,222,255,0.48),transparent_34%)]" />
          <div className="relative">
            <div className="soft-chip mb-6 uppercase tracking-[0.25em] text-fuchsia-600">
              {APP_BRANDING.eyebrow}
            </div>

            <div className="space-y-5">
              <BrandLockup size="lg" />
              <div>
                <h1 className="text-4xl font-black tracking-tight text-slate-900 sm:text-5xl">
                  Acceso a {APP_BRANDING.name}
                </h1>
                <p className="mt-3 max-w-xl text-base leading-7 text-slate-600 sm:text-lg">
                  Inicia sesion con Google usando el correo registrado para tu Tienda o tu cuenta interna.
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="soft-surface p-8 sm:p-10">
          <div className="rounded-[30px] border border-white/90 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(252,246,255,0.96))] p-6 shadow-[0_18px_40px_rgba(181,164,214,0.12)]">
            <div className="mb-6">
              <p className="text-sm font-semibold uppercase tracking-[0.24em] text-violet-500">
                Bienvenida de vuelta
              </p>
              <h2 className="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                Entra con tu cuenta de Google
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                Usa el correo registrado en tu Tienda o en tu cuenta interna para continuar.
              </p>
            </div>

            {accessError ? (
              <div className="mb-5 rounded-[24px] border border-rose-100 bg-[linear-gradient(135deg,rgba(255,244,247,0.98),rgba(255,250,251,0.96))] px-4 py-4 text-sm text-rose-700 shadow-[0_12px_24px_rgba(232,190,201,0.16)]">
                <p className="font-semibold text-rose-800">No pudimos darte acceso a la plataforma</p>
                <p className="mt-1 leading-6">{accessError}</p>
              </div>
            ) : null}

            <a
              href={loginUrl}
              className="group inline-flex w-full items-center justify-center gap-3 rounded-[22px] border border-slate-200 bg-white px-5 py-4 text-sm font-semibold text-slate-800 shadow-[0_14px_26px_rgba(189,195,214,0.16)] transition duration-200 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-[0_18px_30px_rgba(189,195,214,0.22)]"
            >
              <GoogleIcon />
              <span>{isLoading ? "Verificando sesion..." : "Continuar con Google"}</span>
            </a>

            <div className="mt-5 rounded-[24px] border border-violet-100 bg-[linear-gradient(135deg,rgba(250,244,255,0.96),rgba(255,247,251,0.96))] px-4 py-4 text-sm text-slate-600">
              Si aun no puedes entrar, solicita al administrador que confirme el correo de acceso de tu Tienda o tu usuario.
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5">
      <path
        d="M21.8 12.23c0-.76-.07-1.49-.2-2.18H12v4.13h5.5a4.7 4.7 0 0 1-2.04 3.08v2.56h3.3c1.93-1.78 3.04-4.39 3.04-7.59Z"
        fill="#4285F4"
      />
      <path
        d="M12 22c2.75 0 5.06-.91 6.74-2.48l-3.3-2.56c-.92.62-2.09.99-3.44.99-2.64 0-4.88-1.78-5.68-4.17H2.9v2.64A10 10 0 0 0 12 22Z"
        fill="#34A853"
      />
      <path
        d="M6.32 13.78A5.98 5.98 0 0 1 6 12c0-.62.11-1.21.32-1.78V7.58H2.9A10 10 0 0 0 2 12c0 1.61.39 3.13 1.08 4.42l3.24-2.64Z"
        fill="#FBBC05"
      />
      <path
        d="M12 6.05c1.5 0 2.84.52 3.9 1.53l2.92-2.92C17.05 2.99 14.74 2 12 2A10 10 0 0 0 2.9 7.58l3.42 2.64C7.12 7.83 9.36 6.05 12 6.05Z"
        fill="#EA4335"
      />
    </svg>
  );
}
