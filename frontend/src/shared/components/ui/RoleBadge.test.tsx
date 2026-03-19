import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { RoleBadge } from "@/shared/components/ui/RoleBadge";

describe("RoleBadge", () => {
  it("renders friendly Spanish labels", () => {
    render(
      <div>
        <RoleBadge role="ADMIN_SYSTEM" />
        <RoleBadge role="ADMIN_MARKET" />
        <RoleBadge role="STORE_USER" />
      </div>,
    );

    expect(screen.getByText("Administrador General")).toBeInTheDocument();
    expect(screen.getByText("Administrador de Espacio")).toBeInTheDocument();
    expect(screen.getByText("Tienda")).toBeInTheDocument();
  });
});
