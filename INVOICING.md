# Invoicing

Every cart order produces a document. Delivered orders get a numbered **tax invoice**
(or a **bill of supply** if the dairy is not GST registered); orders still on their way get a
**proforma** that is clearly marked as not a tax invoice. The PDF is generated on the fly, attached
to the delivery email, and downloadable from My Orders and the admin order desk for as long as the
order exists.

---

## Contents

1. [The three documents](#1-the-three-documents)
2. [Invoice numbers](#2-invoice-numbers)
3. [Tax](#3-tax)
4. [The PDF writer](#4-the-pdf-writer)
5. [Configuration](#5-configuration)
6. [API](#6-api)
7. [Data model](#7-data-model)
8. [Setting it up](#8-setting-it-up)
9. [What is tested](#9-what-is-tested)
10. [Deliberate limits](#10-deliberate-limits)

---

## 1. The three documents

| Order state | `app.invoice.gstin` set | Document | Numbered | Tax shown |
|---|---|---|---|---|
| Delivered | yes | **Tax Invoice** | yes | CGST + SGST |
| Delivered | no | **Bill of Supply** | yes | none |
| Placed, confirmed, out for delivery | either | **Proforma Invoice** | no | none |
| Cancelled | either | none - **409** | - | - |

The GSTIN is the switch that matters. An unregistered seller must not show or charge GST, so leaving
the GSTIN blank changes the title, drops the tax columns and the tax summary, and prints "No GST has
been charged on this supply." Nothing else about the document changes.

A cancelled order returns 409 with a readable message rather than a document: the money has already
gone back to the customer's wallet, so there is no supply to invoice and no number is spent on it.

**Why a proforma at all.** A customer who has paid wants something on paper before the milk arrives,
and the dairy sometimes needs to send one ahead. A proforma is priced exactly like the final invoice
but carries no number, no tax breakup and a "Not a tax invoice" mark, so it can never be mistaken for
the real document.

## 2. Invoice numbers

Format: `KD/2026-27/000042` - prefix, Indian financial year, six-digit serial.

GST asks three things of an invoice serial: consecutive within a financial year, unique, never
reused. That rules out deriving it from the order id, which skips cancelled orders and runs across
year boundaries.

**How one is issued.** The number is allocated when the order is marked **delivered** - the moment
the supply is complete - inside the same transaction that writes the status, with the order row
already locked by `OrderLifecycleService`. Status and number commit together or not at all.

`InvoiceNumberService.allocate()`:

1. `UPDATE invoice_counters SET last_number = last_number + 1 WHERE financial_year = ?`
   The update takes the row's write lock and holds it until commit, so a second allocation running at
   the same moment blocks rather than reading a stale value.
2. Read the new value back and format it.
3. If the update matched no rows, this is the first invoice of a new financial year. The counter row
   is opened by `InvoiceCounterSeeder` in a **separate transaction** (`REQUIRES_NEW`), and the bump
   is retried. If two requests race to open it, one wins and the loser's insert rolls back on its
   own - the retry then bumps the row the winner created, and the caller's transaction is never
   marked rollback-only.

`orders.invoice_no` carries a unique constraint as the last line of defence.

**Lazily, on first download.** An order that is delivered but has no number - one delivered before
this feature existed, or one whose delivery transaction somehow committed without the stamp - is
numbered the first time its invoice is downloaded, dated from `deliveredAt`. Downloading again always
returns the same number.

> **Watch out for `clearAutomatically`.** The counter's `@Modifying` query uses
> `flushAutomatically = true` but deliberately **not** `clearAutomatically = true`. Clearing detaches
> every entity in the session, including the `Order` the caller is about to stamp, and the stamp is
> then silently dropped at commit. This was a real bug, caught by the test suite: the order came back
> delivered with no number, and the next download issued serial 2 because serial 1 had been rolled
> back. The read after the bump is a scalar projection, so it goes to the database and sees the new
> value without needing the session cleared.

## 3. Tax

**Shelf prices include GST**, which is how Indian retail works, so tax is extracted out of each line
rather than added on top. Per line, with rate `r`:

```
gross   = unit price x quantity
taxable = gross x 100 / (100 + r)     rounded half up
tax     = gross - taxable             so the line always adds back up
```

Working downwards from gross rather than upwards from taxable is what keeps the printed total equal
to the amount the customer was actually charged, to the paisa.

**CGST equals SGST, always.** An odd number of paise of tax cannot be halved evenly, and an invoice
where CGST and SGST differ by a paisa is a question from the auditor. So the odd paisa is moved into
the taxable value, leaving an even tax:

```
if (tax is odd) { taxable += 1; tax -= 1; }
cgst = sgst = tax / 2
```

`taxable + tax` still equals `gross`, so nothing else shifts. This holds on every line and therefore
in every total and in the rate-wise summary.

Everything is computed in whole paise with integer arithmetic through `util/Money`. `BigDecimal` with
`HALF_UP` appears only in the one division above.

**Rounding row.** The invoice total is the sum of the item lines. If that ever drifts from
`order.totalAmount` - a legacy order, a price edited to a fraction of a paisa - the difference is
printed as a "Rounding" row rather than the invoice quietly showing a total nobody paid.

**Rates are per product, set by the admin.** `products.gst_rate_percent` and `products.hsn_code` are
edited in the admin catalogue form; the choices offered are 0, 5, 12 and 18. They are not derived
from the category: which slab a dairy product sits in depends on how it is packed and branded, and
only the dairy knows that. 0 means exempt or nil rated, which is where loose fresh milk sits.

**Rates are snapshotted onto the order line** at purchase, exactly like the name and price. If the
dairy later moves a product to a different slab, or a rate changes, reprinting an old invoice still
shows the tax that was actually charged.

## 4. The PDF writer

`util/pdf/` is a small hand-written PDF 1.4 writer: `PdfDoc` (pages, text, lines, boxes, wrapping,
cross-reference table) and `PdfFont` (Helvetica and Helvetica-Bold with their real Adobe glyph
widths baked in).

**Why not a library.** An invoice is text, rules and boxes on A4. Every PDF reader already carries
Helvetica, so nothing has to be embedded and the only hard parts are glyph widths and byte-exact
object offsets - a few hundred lines. Adding iText would bring an AGPL licence, and PDFBox a
multi-megabyte dependency, to draw twenty lines of text. The writer here has no dependency and no
licence to think about, and an invoice comes out around 8-12 KB.

**Widths matter.** Without real glyph widths there is no right-aligned money column, no centred
heading and no line wrapping. `PdfFont` holds the Adobe metrics for WinAnsi codes 32-255; a code the
font has no glyph for renders as `?` rather than a blank, so text never silently loses characters.

**Layout** (`InvoiceRenderer`): seller band sized from its own address lines, a meta grid (number,
dates, order reference, payment), bill-to and ship-to boxes, the item table, a rate-wise tax summary,
the amount in words beside a totals box, then the declaration, signature block and page numbers. Long
item lists flow onto further pages with the table header repeated, and footers are stamped at the end
once the page count is known, which is what `PdfDoc.selectPage` exists for.

The renderer only formats - every figure arrives already computed, so nothing on the paper can
disagree with what the service worked out.

**"Rs." not the rupee sign.** The standard PDF fonts are WinAnsi-encoded and have no rupee glyph. A
missing glyph on an invoice is worse than three honest letters.

`util/RupeesInWords` writes the amount in words using the Indian numbering system - crore, lakh,
thousand - because that is what an Indian buyer, auditor and accountant read.

## 5. Configuration

Seller identity comes from `app.invoice.*`, backed by environment variables. It is business identity,
not code, and it changes when the dairy registers, moves or renews a licence.

| Property | Environment variable | Meaning |
|---|---|---|
| `app.invoice.seller-name` | `INVOICE_SELLER_NAME` | legal name |
| `app.invoice.trade-name` | `INVOICE_TRADE_NAME` | name shown large at the top |
| `app.invoice.gstin` | `INVOICE_GSTIN` | **blank = bill of supply** |
| `app.invoice.fssai` | `INVOICE_FSSAI` | FSSAI licence number |
| `app.invoice.address-line1` / `-line2` | `INVOICE_ADDRESS_LINE1` / `2` | street address |
| `app.invoice.city` / `.state` / `.pincode` | `INVOICE_CITY` / `STATE` / `PINCODE` | |
| `app.invoice.phone` / `.email` | `INVOICE_PHONE` / `EMAIL` | |
| `app.invoice.prefix` | `INVOICE_PREFIX` | leading segment of the number, default `KD` |
| `app.invoice.signatory` | `INVOICE_SIGNATORY` | name under the signature rule |
| `app.invoice.terms` | `INVOICE_TERMS` | one line under the declaration |
| `app.invoice.declaration` | - | has a sensible default |

## 6. API

| Method | Path | Who | Returns |
|---|---|---|---|
| GET | `/api/orders/{id}/invoice` | the order's owner | `application/pdf` |
| GET | `/api/admin/orders/{id}/invoice` | admin | `application/pdf` |
| GET | `/api/admin/orders/invoice-register.csv?from=&to=` | admin | `text/csv` |

Responses carry the filename as an RFC 6266 attachment, the number in an `X-Invoice-Number` header,
`Access-Control-Expose-Headers` so a cross-origin fetch can read both, and `Cache-Control: no-store` -
an invoice is personal, and a shared proxy has no business keeping a copy.

Another customer's order id returns **404**, never 403: the API must not confirm that somebody else's
order exists.

**The register** is one row per item line, with invoice number, dates, customer, payment method, HSN,
quantity, rate, taxable value, CGST, SGST and line total. Only issued invoices appear, so cancelled
orders never show up. The range is capped at two years per export. This is the file that goes to the
accountant at the end of the month.

## 7. Data model

| Table | Column | Why |
|---|---|---|
| `orders` | `invoice_no` (unique), `invoiced_at` | the issued number and when |
| `order_item` | `hsn_code`, `gst_rate_percent` | snapshotted at purchase |
| `products` | `hsn_code`, `gst_rate_percent` | what a new order copies |
| `invoice_counters` | `financial_year` (PK), `last_number` | one row per year |

All added by Hibernate on the next boot (`ddl-auto=update`). Nothing has to be backfilled: existing
orders invoice as nil-rated and take a number the first time one is downloaded.

## 8. Setting it up

1. Set the `INVOICE_*` environment variables. Leave `INVOICE_GSTIN` blank until the dairy is
   registered - the app then issues a bill of supply, which is the correct document.
2. Restart. Hibernate adds the columns and the counter table.
3. In **Admin → Catalogue**, set the HSN code and GST rate on each product. Anything left alone
   invoices as nil-rated.
4. Deliver an order. The customer gets the PDF by email and can download it again from My Orders.

## 9. What is tested

`run/e2e_invoice.py` - **80 API-level checks** against a real boot of the app:

- proforma before delivery, tax invoice after, 409 on a cancelled order
- the number is issued on delivery, carries the financial year, is stable across downloads, and the
  next order takes the next serial
- **six concurrent deliveries** get six different, consecutive numbers
- taxable + CGST + SGST equals the printed total, which equals what was charged; CGST equals SGST
- nil-rated lines carry no tax; the rate-wise summary groups correctly
- a rate changed after the fact does not alter an old invoice
- a second customer gets 404, anonymous gets 401, a customer cannot use the admin endpoint or pull
  the register
- the register lists issued invoices only, rejects an inverted or over-wide range
- the delivery email carries the PDF as an attachment named after the invoice
- the PDFs pass a `qpdf --check` structural validation and stay small enough to email

Plus a seven-check run with the GSTIN removed for the bill-of-supply path, and a Playwright run
covering the download buttons, the admin reprint, the register modal and the catalogue GST fields on
desktop and at 390 px.

## 10. Deliberate limits

- **Intra-state only.** Tax is split CGST/SGST, which is right for a dairy delivering within
  Maharashtra. Interstate supply would need IGST and a place-of-supply state code.
- **No credit notes.** A cancelled order is refunded to the wallet and simply has no invoice. A
  partial return or a post-delivery refund would need a credit note with its own series.
- **Subscription deliveries are not invoiced** yet - only cart orders. The same service would extend
  to them, most naturally as one monthly invoice per subscriber rather than one per delivery.
- **No e-invoice / IRN.** Not required at this turnover, and it needs registration with the IRP.
