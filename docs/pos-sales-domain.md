# POS sales domain

## Goals

- Support POS-style sales with open, edit, recalculate, and confirm flows.
- Support multi-store sales in a single transaction.
- Support quantity-block promotions per product.
- Support debit-card commissions with per-store and per-line snapshots.
- Preserve traceability for UF value, pricing, and commission breakdown.

## Core sales entities

### Sale

- Represents a POS transaction header.
- Can be `OPEN`, `CONFIRMED`, or `CANCELLED`.
- Stores the selected `paymentMethod`.
- Stores sale-level monetary totals and the `ufValue` snapshot used at confirmation time.

### SaleItem

- Represents a sale line, not a single unit.
- Stores `storeId` because a sale can span multiple stores.
- Stores product snapshots: name, SKU, barcode.
- Stores pricing snapshots:
  - `baseUnitPrice`
  - `lineBaseSubtotal`
  - `promotionDiscountAmount`
  - `subtotal`
  - `pricingType`
  - `appliedPromotionId`
  - `appliedPromotionName`
- Stores commission snapshots:
  - `commission1Amount`
  - `commission2Amount`
  - `commissionVatAmount`
  - `totalCommissionAmount`
  - `netAmount`

### SaleStoreSummary

- Aggregated summary for each store inside a sale.
- Stores:
  - `lineCount`
  - `unitCount`
  - `subtotalAmount`
  - `commission1Amount`
  - `commission2Amount`
  - `commissionVatAmount`
  - `totalCommissionAmount`
  - `netAmount`

This makes dashboards and reconciliation simpler and avoids recalculating per-store totals from item rows on every query.

## Promotion entity

### ProductPromotion

- One row per promotion rule.
- MVP type: `QUANTITY_BLOCK`.
- Example:
  - block quantity `2`
  - block price `15000`

The pricing engine applies complete blocks only and fills the remainder with normal unit pricing.

## Pricing and promotion rules

### Definitions

- A `SaleItem` is one line in the POS, not each unit.
- `precio_venta_item` is the final line subtotal after applying quantity promotions.
- `cantidad_items_tienda` means the number of lines from that store in the sale.

### Promotion calculation

For a product with base unit price `P`, quantity `Q`, and active promotion blocks:

1. Calculate `lineBaseSubtotal = P * Q`.
2. Find the cheapest valid decomposition using:
   - zero or more complete promotion blocks
   - remaining units at normal price
3. Calculate:
   - `subtotal = optimized total`
   - `promotionDiscountAmount = lineBaseSubtotal - subtotal`

For the MVP, a simple dynamic-programming or bounded optimization approach is correct and still lightweight.

## Rounding rule

- Use `BigDecimal` with scale 4 and `HALF_UP` during internal calculations.
- Persist line snapshots exactly as calculated.
- Build store and sale totals from persisted line values to avoid reconciliation drift.

## Debit commission rules

These rules apply only when `paymentMethod = DEBITO`.

### Commission 1

Per store in the sale:

`commission1StoreTotal = 0.00169 * ufValue`

Per line of that store:

`commission1PerItem = commission1StoreTotal / lineCount`

### Commission 2

Per sale line:

`commission2PerItem = saleItem.subtotal * 0.0079`

### VAT on commissions

Per sale line:

`commissionVatPerItem = (commission1PerItem + commission2PerItem) * 0.19`

### Net amount

Only for debit sales:

`netAmount = subtotal - commission1PerItem - commission2PerItem - commissionVatPerItem`

### Non-debit sales

If payment method is not `DEBITO`, all commission snapshot fields remain zero and:

`netAmount = subtotal`

## Recommended calculation flow

1. Create or load an `OPEN` sale.
2. Add, remove, or update lines by barcode, SKU, or name.
3. Recalculate each line subtotal with promotions.
4. Group lines by store.
5. If payment method is `DEBITO`, calculate per-store commission 1 and distribute it by line count.
6. Calculate commission 2 and VAT per line.
7. Update `SaleStoreSummary` rows.
8. Update sale-level totals.
9. Confirm sale in a single transaction:
   - lock products
   - validate stock
   - persist snapshots
   - discount stock
   - write `StockMovement`

## Suggested POS endpoints

### Sales session

- `POST /api/pos/sales`
  - creates an `OPEN` sale
- `GET /api/pos/sales/{saleId}`
  - returns header, lines, summaries, totals
- `POST /api/pos/sales/{saleId}/confirm`
  - confirms the sale
- `POST /api/pos/sales/{saleId}/cancel`
  - cancels an open sale

### Sale items

- `POST /api/pos/sales/{saleId}/items/scan`
  - payload with `query` and optional `quantity`
  - resolves barcode first, then SKU, then name
- `POST /api/pos/sales/{saleId}/items`
  - adds a product line directly
- `PATCH /api/pos/sales/{saleId}/items/{itemId}`
  - changes quantity
- `DELETE /api/pos/sales/{saleId}/items/{itemId}`
  - removes a line

### Pricing and payment

- `PATCH /api/pos/sales/{saleId}/payment-method`
  - updates `paymentMethod` and recalculates totals
- `POST /api/pos/sales/{saleId}/recalculate`
  - recalculates promotions, commissions, and totals

### Search helpers

- `GET /api/pos/products/search?query=...`
  - supports barcode, SKU, and name
- `GET /api/pos/products/{productId}/promotions/active`
  - optional helper for UI inspection
