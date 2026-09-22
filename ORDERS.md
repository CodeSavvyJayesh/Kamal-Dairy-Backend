# Orders and stock

## Order lifecycle

```
PLACED -> CONFIRMED -> OUT_FOR_DELIVERY -> DELIVERED
   \__________\______________\____> CANCELLED
```

- Every paid cart order starts as **PLACED**.
- Only an admin moves it forward. Steps can be skipped, never reversed.
- **Customer cancel:** only while PLACED (before the dairy confirms).
- **Admin cancel:** any open step.
- **Every cancel:** refunds the full amount to the customer's Kamal Wallet
  at once, puts the items back in stock, and emails the customer. All of it
  happens in one transaction.
- Each change locks the order row first. If an admin clicks "Delivered" at
  the same moment a customer clicks "Cancel", only one of them applies, and
  the refund is never paid twice.
- Orders placed before this feature have no status. They read as DELIVERED
  and can no longer change.
- **Emails go out for:** placed, out for delivery, delivered, cancelled.

## Stock

- `products.stock` is the number of units on the shelf. `null` means the
  product is not tracked and is always available, so the existing catalogue
  keeps selling until you set a count.
- **Adding to cart:** the cart refuses more than is in stock (409, "Only 2
  left").
- **Opening Razorpay:** stock is checked before Razorpay opens, so nobody is
  asked to pay for something that is already gone.
- **Placing the order:** the product rows are locked (`SELECT ... FOR
  UPDATE`, in id order), then checked, then decremented. Two buyers can never
  both get the last unit, and stock can never go negative.
- **If an item sells out while a customer is paying on Razorpay:** the
  payment is marked used, its full amount is credited to their wallet, and
  they get a clear 409. This case is committed, not rolled back, so the money
  is never lost.
- **Editing a product** does not change its stock. Stock only changes through
  the stock endpoints, so an edit form opened earlier can never overwrite
  units that orders have taken since.
- Subscriptions are not counted against stock. They are planned supply on the
  dispatch sheet.

## API

Customer (signed in):

| | |
|---|---|
| `GET /api/orders/my-orders` | includes `status`, `statusLabel`, step times, `cancellable`, `refundedAmount` |
| `POST /api/orders/{id}/cancel` `{reason}` | only while PLACED |
| `GET /api/cart` | each row includes `stock` and `imageUrl` |

Admin (`ROLE_ADMIN`):

| | |
|---|---|
| `GET /api/admin/orders?status=&page=&size=` | status: ALL, OPEN, PLACED, CONFIRMED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED |
| `GET /api/admin/orders/stats` | counts per status plus products low on stock |
| `POST /api/admin/orders/{id}/status` `{status}` | move forward |
| `POST /api/admin/orders/{id}/cancel` `{reason}` | refund to wallet and restock |
| `PUT /api/admin/products/{id}/stock` `{stock}` | set the exact count; `null` stops tracking |
| `POST /api/admin/products/{id}/restock` `{quantity}` | a delivery arrived; adds units |
| `GET /api/admin/products/stock-alerts` | tracked products with 5 or fewer left |

Hibernate (`ddl-auto=update`) adds these columns on the next start:

- On `orders`: `status`, `confirmed_at`, `out_for_delivery_at`,
  `delivered_at`, `cancelled_at`, `cancel_reason`, `cancelled_by`,
  `refunded_paise`.
- On `products`: `stock`.
