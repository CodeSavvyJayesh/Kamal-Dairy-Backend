# Subscriptions and wallet

How the subscription engine and prepaid wallet work, what the API looks like,
and how to test it.

---

## The model in one paragraph

A customer tops up a **wallet** through Razorpay. A **subscription** is a
standing instruction – product, quantity, schedule, slot, address. It holds no
money. Every night at the cutoff (11 PM IST by default) the **engine** turns
each active subscription that is due tomorrow into one **delivery** row and
charges that one delivery to the wallet. If the balance is short, the delivery
is recorded as MISSED, nothing is charged, and the customer gets an email.
Admins see tomorrow's deliveries as a dispatch sheet, mark them delivered, or
refund them to the wallet.

## Plans and schedules

| Plan | Schedules | Discount |
|---|---|---|
| Daily Delivery | every day, alternate days, chosen weekdays | 0% |
| Weekly Essentials | one chosen weekday | 8% |
| Monthly Smart Saver | every 30 days from the start date | 10% |

Discounts live in one place: `SubscriptionFrequency`. The marketing page, the
price preview and the nightly charge all read from it.

## The cutoff rule

Changes for a date close at the cutoff on the evening before it. Before that
the customer can skip it, pause, set a vacation, or change quantity, schedule,
slot or address. After it, that date is generated and charged, and is history.

`DeliveryCalendar.firstEditableDate()` is the single source of that rule: it is
tomorrow before the cutoff, and the day after tomorrow once it has passed.
Every mutation is validated against it on the server.

## Why the money cannot go wrong

- **Paise, not rupees.** All wallet and delivery amounts are `long` paise.
  `Money` converts at the edges. No floating point touches a balance.
- **No lost updates.** The balance only changes through one conditional
  `UPDATE ... SET balance = balance - :amt WHERE balance >= :amt`. The row lock
  it takes is held until commit, so concurrent debits serialise and the balance
  can never go negative.
- **Append-only ledger.** Every change writes one `wallet_transactions` row with
  the balance after it, in the same transaction. The ledger always reconciles
  to the balance.
- **No double charges.** `subscription_deliveries` has a unique key on
  `(subscription_id, delivery_date)`. The delivery row is inserted *before* the
  wallet is debited, so a second attempt fails to insert and its debit rolls
  back with it.
- **One transaction per subscription.** `SubscriptionDeliveryProcessor.process`
  runs with `REQUIRES_NEW`. One bad subscription cannot roll back the night.
- **Top-ups are proven.** The amount credited is the one the server recorded
  when it created the Razorpay order. The browser's receipt must carry a valid
  HMAC signature, belong to the caller, be marked as a top-up (never a cart
  order), and can be redeemed exactly once – replay-safe under concurrency.

## The nightly run

`SubscriptionEngine` runs every ten minutes and once at startup. Each time it:

1. marks anything still SCHEDULED from a previous day as DELIVERED
2. generates **today** if today has not been generated yet (catch-up)
3. generates **tomorrow** if the cutoff has passed and tomorrow has not been generated

Each generated date is recorded in `subscription_generation_runs` and never
generated again. That is what makes it safe to run often, safe across
restarts, and why a server that was asleep at 11 PM catches up as soon as it
wakes.

> **Render free tier:** the instance sleeps when idle, so nothing runs while it
> is asleep. Deliveries are generated the moment it next wakes. For real
> operations use a paid instance, or point an uptime pinger at `/test`.

## Configuration

All optional – the defaults are shown.

```properties
app.timezone=Asia/Kolkata
app.subscription.cutoff-hour=23
app.subscription.generation-cron=0 */10 * * * *
```

Hibernate (`ddl-auto=update`) creates the new tables on first start:
`wallets`, `wallet_transactions`, `subscriptions`, `subscription_skips`,
`subscription_deliveries`, `subscription_generation_runs`. It also adds
`payment_method` to `orders` and `purpose` to `payment_orders`.

## API

Public:

| | |
|---|---|
| `GET /api/subscriptions/plans` | schedules, discounts, slots, cutoff, first editable date |
| `POST /api/subscriptions/preview` | server-computed price for a product + schedule |

Signed in (the owner is always the logged-in user):

| | |
|---|---|
| `GET /api/wallet` | balance, forecast, recent transactions |
| `GET /api/wallet/transactions?page=&size=` | full ledger |
| `POST /api/wallet/topup` `{amount}` | Razorpay order for a top-up (Rs 50 – 10,000) |
| `POST /api/wallet/topup/verify` | Razorpay receipt → credit |
| `POST /api/orders/place-with-wallet` `{address}` | pay the cart from the wallet (402 if short) |
| `GET/POST /api/subscriptions` | list / create |
| `GET/PUT /api/subscriptions/{id}` | detail with 14-day calendar / update |
| `POST /api/subscriptions/{id}/pause`, `/resume`, `/cancel` | |
| `PUT/DELETE /api/subscriptions/{id}/vacation` | set / end vacation |
| `POST /api/subscriptions/{id}/skips` `{date}` | skip one day |
| `DELETE /api/subscriptions/{id}/skips/{date}` | restore it |
| `GET /api/subscriptions/deliveries?subscriptionId=` | delivery history |

Admin only (`ROLE_ADMIN`):

| | |
|---|---|
| `GET /api/admin/subscriptions/stats` | active, tomorrow, MRR, wallet float |
| `GET /api/admin/subscriptions/dispatch?date=` | dispatch sheet (defaults to tomorrow) |
| `POST /api/admin/subscriptions/deliveries/{id}/delivered` | |
| `POST /api/admin/subscriptions/deliveries/{id}/refund` `{reason}` | refund to wallet |
| `POST /api/admin/subscriptions/run?date=` | generate today, or tomorrow after the cutoff |

## Testing it yourself

Unit tests for the schedule rules and money maths:

```
mvn test -Dtest=SubscriptionScheduleTest
```

End to end, with Razorpay test keys:

1. Top up Rs 500 from the Wallet page with a test card.
2. Create a daily subscription starting tomorrow.
3. Skip a day, set a vacation, and check the calendar.
4. As admin, open Admin → Subscriptions. Tomorrow shows as *projected*.
5. To see the nightly charge now, restart the backend with
   `APP_SUBSCRIPTION_CUTOFF_HOUR=1` (or any hour already passed today).
   Tomorrow is generated at startup, the wallet is debited, and the dispatch
   sheet fills. Set it back to 23 afterwards.
