import { AppProviders } from "@/app/providers/AppProviders";
import { AppRouter } from "@/routes/router";

export function App() {
  return (
    <AppProviders>
      <AppRouter />
    </AppProviders>
  );
}
