import { FormEvent, type Dispatch, type SetStateAction, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useSession } from "@/features/auth/session/SessionProvider";
import {
  createProduct,
  getProductAudit,
  listProducts,
  printBarcodeLabels,
  type Product,
  type ProductAuditLog,
  type ProductPromotion,
  type ProductPromotionInput,
  updateProduct,
} from "@/features/products/api/productApi";
import { listUsers } from "@/features/users/api/userApi";
import { EmptyState } from "@/shared/components/feedback/EmptyState";
import { FeedbackMessage } from "@/shared/components/feedback/FeedbackMessage";
import { Modal } from "@/shared/components/ui/Modal";
import { PageHeader } from "@/shared/components/ui/PageHeader";
import { ApiError } from "@/shared/lib/api/client";

type ProductFormState = {
  name: string;
  ownerUserId: string;
  salePrice: string;
  stock: string;
  description: string;
  promotionType: "NONE" | "QUANTITY_BLOCK" | "PERCENTAGE_DISCOUNT";
  promotionQuantity: string;
  promotionPrice: string;
  promotionPercentage: string;
};

type BarcodeModalState = {
  items: Array<{ productId: number; productName: string; quantity: string }>;
};

const EMPTY_FORM: ProductFormState = {
  name: "",
  ownerUserId: "",
  salePrice: "",
  stock: "0",
  description: "",
  promotionType: "NONE",
  promotionQuantity: "",
  promotionPrice: "",
  promotionPercentage: "",
};

export function ProductsPage() {
  const queryClient = useQueryClient();
  const { user } = useSession();
  const activeMarketName = user?.activeMarketName ?? "tu Tienda";
  const activeMarketId = user?.marketIds?.[0] ?? null;
  const [search, setSearch] = useState("");
  const [feedback, setFeedback] = useState<{ kind: "success" | "error" | "info"; message: string } | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editProduct, setEditProduct] = useState<Product | null>(null);
  const [createForm, setCreateForm] = useState<ProductFormState>(EMPTY_FORM);
  const [editForm, setEditForm] = useState<ProductFormState>(EMPTY_FORM);
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>([]);
  const [barcodeModal, setBarcodeModal] = useState<BarcodeModalState | null>(null);

  const productsQuery = useQuery({
    queryKey: ["products", "catalog", search],
    queryFn: () => listProducts({ size: 100, query: search || undefined }),
  });

  const usersQuery = useQuery({
    queryKey: ["users", "catalog"],
    queryFn: listUsers,
  });

  const collaborators = useMemo(
    () =>
      (usersQuery.data ?? []).filter((candidate) => {
        if (!candidate.active || !candidate.roles.includes("STORE_USER")) {
          return false;
        }
        return activeMarketId ? candidate.marketIds.includes(activeMarketId) || candidate.storeIds.length > 0 : true;
      }),
    [activeMarketId, usersQuery.data],
  );

  const selectedProducts = useMemo(
    () => productsQuery.data?.content.filter((product) => selectedProductIds.includes(product.id)) ?? [],
    [productsQuery.data, selectedProductIds],
  );

  const auditQuery = useQuery({
    queryKey: ["products", "audit", editProduct?.id],
    queryFn: () => getProductAudit(editProduct!.id),
    enabled: editProduct !== null,
  });

  useEffect(() => {
    if (editProduct) {
      setEditForm(toFormState(editProduct));
    }
  }, [editProduct]);

  useEffect(() => {
    setSelectedProductIds((current) =>
      current.filter((id) => productsQuery.data?.content.some((product) => product.id === id)),
    );
  }, [productsQuery.data]);

  const createMutation = useMutation({
    mutationFn: () =>
      createProduct({
        ownerUserId: Number(createForm.ownerUserId),
        name: createForm.name.trim(),
        sku: buildSku(createForm.name),
        description: createForm.description.trim() || undefined,
        salePrice: Number(createForm.salePrice),
        initialStock: Number(createForm.stock),
        promotion: buildPromotionInput(createForm),
      }),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Producto creado correctamente." });
      setCreateOpen(false);
      setCreateForm(EMPTY_FORM);
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos crear el producto.") }),
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      updateProduct(editProduct!.id, {
        ownerUserId: Number(editForm.ownerUserId),
        name: editForm.name.trim(),
        sku: editProduct!.sku,
        description: editForm.description.trim() || undefined,
        salePrice: Number(editForm.salePrice),
        stock: Number(editForm.stock),
        promotion: buildPromotionInput(editForm),
      }),
    onSuccess: async () => {
      setFeedback({ kind: "success", message: "Producto actualizado correctamente." });
      setEditProduct(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "audit"] }),
      ]);
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos actualizar el producto.") }),
  });

  const barcodeMutation = useMutation({
    mutationFn: () =>
      printBarcodeLabels({
        items: (barcodeModal?.items ?? []).map((item) => ({
          productId: item.productId,
          quantity: Number(item.quantity),
        })),
        includeCollaboratorName: true,
      }),
    onSuccess: async (blob) => {
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "codigos-colabohub.pdf";
      anchor.click();
      window.URL.revokeObjectURL(url);
      setBarcodeModal(null);
      setFeedback({ kind: "success", message: "PDF de codigos generado correctamente." });
      await Promise.resolve();
    },
    onError: (error) => setFeedback({ kind: "error", message: getErrorMessage(error, "No pudimos generar el PDF.") }),
  });

  const allVisibleSelected =
    (productsQuery.data?.content.length ?? 0) > 0 &&
    (productsQuery.data?.content.every((product) => selectedProductIds.includes(product.id)) ?? false);

  return (
    <section className="space-y-6">
      <PageHeader
        title="Stock"
        description="Gestiona productos, promociones y codigos de barra con una vista amplia y comoda para operar tu Tienda."
        eyebrow="Catalogo principal"
      />

      {feedback ? <FeedbackMessage kind={feedback.kind} message={feedback.message} /> : null}

      <div className="soft-surface p-6">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Catalogo de {activeMarketName}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Cada producto se vincula a un Colaborador y puede tener una sola promocion activa.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por nombre o SKU"
              className="min-w-[240px] rounded-full border border-white/85 bg-white/80 px-4 py-3 text-sm shadow-sm outline-none transition focus:border-violet-200 focus:bg-white"
            />
            <button
              type="button"
              onClick={() => {
                setCreateForm(EMPTY_FORM);
                setCreateOpen(true);
              }}
              disabled={collaborators.length === 0}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-5 py-3 text-sm font-semibold text-white shadow-[0_14px_28px_rgba(186,153,228,0.24)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Crear producto
            </button>
          </div>
        </div>

        {collaborators.length === 0 ? (
          <div className="mt-5">
            <FeedbackMessage kind="info" message="Crea al menos un Colaborador para poder asignar productos." />
          </div>
        ) : null}

        <div className="mt-6 flex flex-col gap-3 rounded-[24px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.94),rgba(255,247,251,0.92))] p-4 shadow-[0_16px_40px_rgba(186,170,211,0.08)] sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className="soft-chip">{productsQuery.data?.totalElements ?? 0} productos</span>
            <span className="soft-chip">{selectedProductIds.length} seleccionados</span>
          </div>
          <button
            type="button"
            onClick={() =>
              setBarcodeModal({
                items: selectedProducts.map((product) => ({
                  productId: product.id,
                  productName: product.name,
                  quantity: "1",
                })),
              })
            }
            disabled={selectedProducts.length === 0}
            className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Imprimir codigos seleccionados
          </button>
        </div>

        {productsQuery.isLoading ? <div className="mt-6"><FeedbackMessage kind="info" message="Cargando catalogo..." /></div> : null}
        {productsQuery.isError ? (
          <div className="mt-6">
            <FeedbackMessage kind="error" message={getErrorMessage(productsQuery.error, "No pudimos cargar el catalogo.")} />
          </div>
        ) : null}

        {productsQuery.data?.empty ? (
          <div className="mt-6">
            <EmptyState
              title="Todavia no hay productos en tu Tienda"
              description="Crea tu primer producto para empezar a vender, imprimir codigos y organizar el stock."
            />
          </div>
        ) : (
          <div className="soft-table mt-6">
            <table>
              <thead>
                <tr>
                  <th className="w-[28%]">
                    <label className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={allVisibleSelected}
                        onChange={() =>
                          setSelectedProductIds(
                            allVisibleSelected ? [] : (productsQuery.data?.content.map((product) => product.id) ?? []),
                          )
                        }
                      />
                      <span>Producto</span>
                    </label>
                  </th>
                  <th>Colaborador</th>
                  <th>Precio</th>
                  <th>Stock</th>
                  <th>Descripcion corta</th>
                  <th>Promocion</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {productsQuery.data?.content.map((product) => (
                  <tr key={product.id}>
                    <td>
                      <label className="flex items-start gap-3">
                        <input
                          type="checkbox"
                          checked={selectedProductIds.includes(product.id)}
                          onChange={() => toggleSelection(product.id, setSelectedProductIds)}
                        />
                        <div>
                          <p className="font-semibold">{product.name}</p>
                          <p className="text-xs text-muted-foreground">SKU {product.sku}</p>
                        </div>
                      </label>
                    </td>
                    <td>{product.ownerFullName ?? "Sin asignar"}</td>
                    <td>{formatCurrency(product.salePrice)}</td>
                    <td>
                      <span className={["soft-chip", product.stock <= 5 ? "text-rose-700" : "text-emerald-700"].join(" ")}>
                        {product.stock}
                      </span>
                    </td>
                    <td>{truncate(product.description ?? "Sin descripcion", 44)}</td>
                    <td>
                      <span className={product.hasPromotion ? "soft-chip text-fuchsia-700" : "soft-chip text-muted-foreground"}>
                        {product.hasPromotion ? "SI" : "NO"}
                      </span>
                    </td>
                    <td>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => setEditProduct(product)}
                          className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            setBarcodeModal({
                              items: [{ productId: product.id, productName: product.name, quantity: "1" }],
                            })
                          }
                          className="rounded-full border border-white/90 bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground shadow-sm transition hover:-translate-y-0.5"
                        >
                          Imprimir codigos
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Crear producto"
        description="La Tienda se resuelve automaticamente desde tu sesion. Solo define el producto y su Colaborador responsable."
        footer={
          <FooterActions
            submitLabel={createMutation.isPending ? "Creando..." : "Guardar producto"}
            formId="create-product-form"
            onCancel={() => setCreateOpen(false)}
            disabled={createMutation.isPending}
          />
        }
      >
        <ProductForm
          id="create-product-form"
          form={createForm}
          onChange={setCreateForm}
          collaborators={collaborators.map((collaborator) => ({ id: collaborator.id, fullName: collaborator.fullName }))}
          onSubmit={(event) => {
            event.preventDefault();
            setFeedback(null);
            createMutation.mutate();
          }}
          stockLabel="Stock inicial"
        />
      </Modal>

      <Modal
        open={editProduct !== null}
        onClose={() => setEditProduct(null)}
        title={editProduct ? `Editar ${editProduct.name}` : "Editar producto"}
        description="Actualiza precio, stock, promocion y revisa el historial completo del producto."
        footer={
          <FooterActions
            submitLabel={updateMutation.isPending ? "Guardando..." : "Guardar cambios"}
            formId="edit-product-form"
            onCancel={() => setEditProduct(null)}
            disabled={updateMutation.isPending || editProduct === null}
          />
        }
      >
        {editProduct ? (
          <div className="space-y-6">
            <div className="grid gap-3 md:grid-cols-3">
              <InfoCard label="Codigo de barras" value={editProduct.barcode} />
              <InfoCard label="Promocion activa" value={describePromotion(editProduct.promotion)} />
              <InfoCard label="Ultima actualizacion" value={formatDate(editProduct.updatedAt)} />
            </div>

            <ProductForm
              id="edit-product-form"
              form={editForm}
              onChange={setEditForm}
              collaborators={collaborators.map((collaborator) => ({ id: collaborator.id, fullName: collaborator.fullName }))}
              onSubmit={(event) => {
                event.preventDefault();
                setFeedback(null);
                updateMutation.mutate();
              }}
              stockLabel="Stock actual"
            />

            <div className="rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.96),rgba(255,247,251,0.94))] p-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold">Historial del producto</h3>
                  <p className="text-sm text-muted-foreground">Aqui veras quien cambio precio, stock, descripcion o promocion.</p>
                </div>
                <span className="soft-chip">{auditQuery.data?.length ?? 0} eventos</span>
              </div>

              {auditQuery.isLoading ? <div className="mt-4"><FeedbackMessage kind="info" message="Cargando historial..." /></div> : null}
              {auditQuery.isError ? (
                <div className="mt-4">
                  <FeedbackMessage kind="error" message={getErrorMessage(auditQuery.error, "No pudimos cargar el historial.")} />
                </div>
              ) : null}
              {auditQuery.data?.length === 0 ? (
                <div className="mt-4">
                  <EmptyState
                    title="Sin cambios registrados aun"
                    description="Cuando hagas ajustes de producto, el historial aparecera aqui."
                  />
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  {auditQuery.data?.map((entry) => (
                    <AuditRow key={entry.id} entry={entry} />
                  ))}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        open={barcodeModal !== null}
        onClose={() => setBarcodeModal(null)}
        title="Imprimir codigos de barras"
        description="Configura la cantidad de etiquetas por producto y descarga un PDF listo para imprimir."
        footer={
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setBarcodeModal(null)}
              className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={() => barcodeMutation.mutate()}
              disabled={barcodeMutation.isPending || !(barcodeModal?.items.length)}
              className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
            >
              {barcodeMutation.isPending ? "Generando..." : "Descargar PDF"}
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          {barcodeModal?.items.map((item) => (
            <div key={item.productId} className="flex flex-col gap-3 rounded-[22px] border border-white/80 bg-background/70 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-medium">{item.productName}</p>
                <p className="text-sm text-muted-foreground">Etiquetas pequenas en hoja A4 con barcode CODE128.</p>
              </div>
              <label className="grid gap-2 text-sm sm:w-36">
                <span>Cantidad</span>
                <input
                  min="1"
                  type="number"
                  value={item.quantity}
                  onChange={(event) =>
                    setBarcodeModal((current) =>
                      current
                        ? {
                            items: current.items.map((candidate) =>
                              candidate.productId === item.productId ? { ...candidate, quantity: event.target.value } : candidate,
                            ),
                          }
                        : current,
                    )
                  }
                  className="rounded-2xl border border-input bg-background px-3 py-2"
                />
              </label>
            </div>
          ))}
        </div>
      </Modal>
    </section>
  );
}

function ProductForm({
  id,
  form,
  onChange,
  collaborators,
  onSubmit,
  stockLabel,
}: {
  id: string;
  form: ProductFormState;
  onChange: (next: ProductFormState) => void;
  collaborators: Array<{ id: number; fullName: string }>;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  stockLabel: string;
}) {
  return (
    <form id={id} onSubmit={onSubmit} className="grid gap-5">
      <div className="grid gap-5 md:grid-cols-2">
        <label className="grid gap-2 text-sm">
          <span>Nombre del producto</span>
          <input required value={form.name} onChange={(event) => onChange({ ...form, name: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>Colaborador responsable</span>
          <select required value={form.ownerUserId} onChange={(event) => onChange({ ...form, ownerUserId: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5">
            <option value="">Selecciona un Colaborador</option>
            {collaborators.map((collaborator) => (
              <option key={collaborator.id} value={collaborator.id}>
                {collaborator.fullName}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-2 text-sm">
          <span>Precio de venta</span>
          <input required min="0.01" step="0.01" type="number" value={form.salePrice} onChange={(event) => onChange({ ...form, salePrice: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
        <label className="grid gap-2 text-sm">
          <span>{stockLabel}</span>
          <input required min="0" step="1" type="number" value={form.stock} onChange={(event) => onChange({ ...form, stock: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
        </label>
      </div>

      <label className="grid gap-2 text-sm">
        <span>Descripcion</span>
        <textarea rows={4} value={form.description} onChange={(event) => onChange({ ...form, description: event.target.value })} className="rounded-[24px] border border-input bg-background px-3 py-3" />
      </label>

      <div className="rounded-[26px] border border-white/80 bg-[linear-gradient(180deg,rgba(255,252,255,0.98),rgba(255,246,250,0.94))] p-5">
        <div className="flex flex-col gap-1">
          <h3 className="text-base font-semibold">Promocion</h3>
          <p className="text-sm text-muted-foreground">Elige una promocion por cantidad o un descuento porcentual.</p>
        </div>

        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <label className="grid gap-2 text-sm">
            <span>Tipo de promocion</span>
            <select
              value={form.promotionType}
              onChange={(event) =>
                onChange({
                  ...form,
                  promotionType: event.target.value as ProductFormState["promotionType"],
                  promotionQuantity: "",
                  promotionPrice: "",
                  promotionPercentage: "",
                })
              }
              className="rounded-2xl border border-input bg-background px-3 py-2.5"
            >
              <option value="NONE">Sin promocion</option>
              <option value="QUANTITY_BLOCK">Promocion por cantidad</option>
              <option value="PERCENTAGE_DISCOUNT">Descuento porcentual</option>
            </select>
          </label>

          {form.promotionType === "QUANTITY_BLOCK" ? (
            <>
              <label className="grid gap-2 text-sm">
                <span>Cantidad</span>
                <input required min="2" step="1" type="number" value={form.promotionQuantity} onChange={(event) => onChange({ ...form, promotionQuantity: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
              </label>
              <label className="grid gap-2 text-sm">
                <span>Precio promocional</span>
                <input required min="0.01" step="0.01" type="number" value={form.promotionPrice} onChange={(event) => onChange({ ...form, promotionPrice: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
              </label>
            </>
          ) : null}

          {form.promotionType === "PERCENTAGE_DISCOUNT" ? (
            <label className="grid gap-2 text-sm">
              <span>Porcentaje</span>
              <input required min="0.01" max="100" step="0.01" type="number" value={form.promotionPercentage} onChange={(event) => onChange({ ...form, promotionPercentage: event.target.value })} className="rounded-2xl border border-input bg-background px-3 py-2.5" />
            </label>
          ) : null}
        </div>
      </div>
    </form>
  );
}

function FooterActions({ submitLabel, formId, onCancel, disabled }: { submitLabel: string; formId: string; onCancel: () => void; disabled: boolean }) {
  return (
    <div className="flex justify-end gap-3">
      <button type="button" onClick={onCancel} className="rounded-full border border-white/90 bg-white/80 px-4 py-2.5 text-sm font-semibold text-muted-foreground">
        Cancelar
      </button>
      <button type="submit" form={formId} disabled={disabled} className="rounded-full bg-[linear-gradient(135deg,rgba(192,162,244,1),rgba(247,175,215,0.96))] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50">
        {submitLabel}
      </button>
    </div>
  );
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="soft-subtle-surface p-4">
      <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-sm font-semibold">{value}</p>
    </div>
  );
}

function AuditRow({ entry }: { entry: ProductAuditLog }) {
  return (
    <div className="rounded-[22px] border border-white/80 bg-white/80 px-4 py-4 shadow-sm">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="font-medium">{entry.fieldName}</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {entry.previousValue ?? "Sin valor previo"} {"->"} {entry.newValue ?? "Sin valor nuevo"}
          </p>
        </div>
        <div className="text-right text-xs text-muted-foreground">
          <p>{entry.createdBy}</p>
          <p>{formatDate(entry.createdAt)}</p>
        </div>
      </div>
    </div>
  );
}

function toFormState(product: Product): ProductFormState {
  return {
    name: product.name,
    ownerUserId: product.ownerUserId ? String(product.ownerUserId) : "",
    salePrice: String(product.salePrice),
    stock: String(product.stock),
    description: product.description ?? "",
    promotionType: resolvePromotionType(product.promotion),
    promotionQuantity: product.promotion?.type === "QUANTITY_BLOCK" ? String(product.promotion.quantity ?? "") : "",
    promotionPrice: product.promotion?.type === "QUANTITY_BLOCK" ? String(product.promotion.promotionalPrice ?? "") : "",
    promotionPercentage: product.promotion?.type === "PERCENTAGE_DISCOUNT" ? String(product.promotion.percentageDiscount ?? "") : "",
  };
}

function resolvePromotionType(promotion: ProductPromotion | null): ProductFormState["promotionType"] {
  if (!promotion) {
    return "NONE";
  }
  return promotion.type;
}

function buildPromotionInput(form: ProductFormState): ProductPromotionInput | undefined {
  if (form.promotionType === "NONE") {
    return undefined;
  }
  if (form.promotionType === "QUANTITY_BLOCK") {
    return {
      type: "QUANTITY_BLOCK",
      quantity: Number(form.promotionQuantity),
      promotionalPrice: Number(form.promotionPrice),
    };
  }
  return {
    type: "PERCENTAGE_DISCOUNT",
    percentageDiscount: Number(form.promotionPercentage),
  };
}

function buildSku(name: string) {
  const base = name
    .trim()
    .toUpperCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
  return base || `PRODUCTO-${Date.now()}`;
}

function toggleSelection(productId: number, setSelected: Dispatch<SetStateAction<number[]>>) {
  setSelected((current) => (current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId]));
}

function truncate(value: string, maxLength: number) {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength - 1)}...`;
}

function describePromotion(promotion: ProductPromotion | null) {
  if (!promotion) {
    return "Sin promocion";
  }
  if (promotion.type === "QUANTITY_BLOCK") {
    return `${promotion.quantity} x ${formatCurrency(promotion.promotionalPrice ?? 0)}`;
  }
  return `${promotion.percentageDiscount}% descuento`;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(value);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-CL", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}
