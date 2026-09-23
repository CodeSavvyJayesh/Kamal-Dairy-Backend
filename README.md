# Kamal Dairy - Backend

Spring Boot API for **Kamal Dairy**, a dairy e-commerce platform: one-off cart orders, recurring
milk subscriptions billed from a prepaid wallet, Razorpay payments, stock control, an order
lifecycle with refunds, verified-buyer product reviews, and an admin console with sales analytics.

| | |
|---|---|
| **Stack** | Java 21, Spring Boot 3.5.9, Spring Security 6, Spring Data JPA / Hibernate 6.6, MySQL 8 |
| **Auth** | Stateless JWT (JJWT 0.11.5), BCrypt, email OTP verification |
| **Payments** | Razorpay Java SDK 1.4.4 (orders + HMAC-SHA256 signature verification) |
| **Mail** | Spring Mail over Gmail SMTP |
| **Build** | Maven (`./mvnw`), fat JAR, Dockerfile included |
| **Frontend** | [`Kamal-Dairy`](../Kamal-Dairy) - React 19 + Vite |

---

## Contents

1. [What the system does](#1-what-the-system-does)
2. [Architecture](#2-architecture)
3. [Feature guide](#3-feature-guide)
   - [3.1 Accounts, OTP and login protection](#31-accounts-otp-and-login-protection)
   - [3.2 Products and stock](#32-products-and-stock)
   - [3.3 Cart, address and checkout](#33-cart-address-and-checkout)
   - [3.4 Payments](#34-payments)
   - [3.5 Order lifecycle](#35-order-lifecycle)
   - [3.6 Wallet](#36-wallet)
   - [3.7 Subscriptions](#37-subscriptions)
   - [3.8 Reviews and ratings](#38-reviews-and-ratings)
   - [3.9 Sales analytics](#39-sales-analytics)
   - [3.10 Email](#310-email)
4. [API reference](#4-api-reference)
5. [Data model](#5-data-model)
6. [Money, time and concurrency rules](#6-money-time-and-concurrency-rules)
7. [Configuration](#7-configuration)
8. [Running locally](#8-running-locally)
9. [Deployment](#9-deployment)
10. [Testing](#10-testing)
11. [Further reading](#11-further-reading)

---

## 1. What the system does

**For customers**

- Sign up with email, verify with a 6-digit OTP, log in, reset a forgotten password.
- Browse products by category, read reviews, see live stock.
- Add to cart, enter a delivery address, pay with Razorpay **or** the prepaid wallet.
- Track an order through Placed → Confirmed → Out for delivery → Delivered, and cancel while it is
  still open (the money goes straight back to the wallet).
- Build a recurring subscription (daily / alternate days / weekly / custom weekdays), pick a slot,
  pause, skip a date, set a vacation window, or cancel. Deliveries are billed from the wallet.
- Top up the wallet, see every credit and debit, and get a forecast of how long the balance lasts.
- Review a product once it has actually been delivered, edit or delete that review later.

**For the admin**

- Products: create, edit, delete, set or add stock, low-stock alerts.
- Orders: filter by status, advance a status, cancel with an automatic wallet refund and restock.
- Subscriptions: today's dispatch sheet, mark delivered, refund a delivery, force a generation run.
- Reviews: moderation desk with public replies, hide / show, and filters for 1-2 star and unanswered.
- Insights: revenue by day (cart vs subscription), best sellers, new vs returning buyers, plan
  performance, payment mix, and a live subscription snapshot, all compared against the previous period.

---

## 2. Architecture

A single Spring Boot service, layered controller → service → repository, with all business rules in
the service layer. No mapper framework: DTOs are Java `record`s built by hand.

```
com.kamaldairy.kamal_dairy_backend
├── config/        RazorpayConfig, SchedulingConfig, SecurityConfig
├── controller/    thin HTTP layer, no logic
├── dto/           request/response records
├── exception/     typed exceptions + GlobalExceptionHandler
├── model/         JPA entities and enums
├── repository/    Spring Data JPA, incl. pessimistic-lock and conditional-update queries
├── security/      JwtUtil, JwtAuthenticationFilter, AuthGuard, ClientIp
├── service/       all business logic
└── util/          Money, Addresses
```

**Services at a glance**

| Service | Responsibility |
|---|---|
| `UserService` | signup, OTP verify, login, resend, forgot / reset password |
| `ProductService` | catalogue reads, paging, category filters |
| `StockService` | reserve, release, set, add, low-stock queries |
| `CartService` | cart CRUD, stock-aware |
| `OrderService` | checkout from cart (Razorpay or wallet), stock capture |
| `OrderLifecycleService` | status transitions, customer / admin cancel, refunds, admin listing, stats |
| `PaymentService` | Razorpay order creation and signature verification |
| `WalletService` | balance, ledger, top-up, debit, credit, forecast |
| `SubscriptionService` | subscription CRUD, pause, resume, skip, vacation, previews |
| `SubscriptionEngine` | nightly generation of the next delivery per subscription |
| `SubscriptionDeliveryProcessor` | bills a delivery from the wallet, handles failure |
| `SubscriptionSchedule` | pure date maths for every frequency |
| `SubscriptionAdminService` | dispatch sheet, mark delivered, refund, stats |
| `DeliveryCalendar` | the single source of "now" in IST, plus the daily cut-off |
| `ReviewService` | eligibility, write / edit / delete, moderation, rating rollups |
| `AnalyticsService` | the whole sales dashboard in one pass |
| `EmailService` | every transactional email |
| `TrendingProductService` | home page picks |
| `PasswordRules` | one place for password policy |

**Cross-cutting**

- `GlobalExceptionHandler` maps every typed exception to a clean JSON body and the right status
  (400 / 401 / 403 / 404 / 409 / 429 / 500), including a handler for unknown URLs so a typo returns
  404 rather than a logged 500.
- `SecurityConfig` is stateless: CSRF off, CORS from `app.cors.allowed-origins`, public routes
  whitelisted, `/api/admin/**` restricted to `ROLE_ADMIN`, everything else authenticated.
- `SchedulingConfig` enables `@Scheduled`, used by the subscription generator and the `AuthGuard` sweep.

---

## 3. Feature guide

### 3.1 Accounts, OTP and login protection

**Sign up → verify → log in**

1. `POST /api/auth/signup` creates a disabled user, hashes the password with BCrypt, generates a
   6-digit OTP, stores only its **BCrypt hash** with an expiry, and mails the code.
   Signing up again with an email that exists but is still unverified simply re-sends a fresh code
   instead of failing, so a lost email is never a dead end.
2. `POST /api/auth/verify` checks the code. Five wrong attempts burn the code and it must be resent.
   The attempt counter is committed even when the request returns an error
   (`@Transactional(noRollbackFor = ApiException.class)`), so a rollback cannot reset the counter.
3. `POST /api/auth/login` returns a JWT plus the role.

**Forgot password**

`POST /api/auth/forgot-password` always returns the same generic message whether or not the address
exists, so the endpoint cannot be used to enumerate users. The mail is sent asynchronously so the
response time does not leak anything either. `POST /api/auth/reset-password` takes the code and the
new password, and on success it:

- validates the new password against `PasswordRules`,
- enables the account if it was still unverified,
- clears any lockout,
- **bumps the user's `tokenVersion`**, and
- returns a fresh `LoginResponse` so the user lands logged in.

**Token versioning.** Every JWT carries a `tv` claim. `JwtAuthenticationFilter` rejects a token
whose `tv` does not match the user's current `tokenVersion`, so resetting a password instantly kills
every session that was open on another device.

**Rate limiting and lockout - `security/AuthGuard`**

An in-memory guard with fixed windows, swept every 10 minutes by a `@Scheduled` task. The client IP
comes from `ClientIp`, which trusts the first entry of `X-Forwarded-For` when present (needed behind
Nginx and Render).

| Guard | Limit |
|---|---|
| Failed logins | 5 per account, then a 15-minute lock |
| Per-IP auth traffic | windowed cap across login / signup / verify / forgot / reset / resend |
| Outgoing codes, per address | 1 per cooldown (`app.auth.code-cooldown-seconds`, default 60) and 5 per hour |
| Outgoing codes, global | 100 per hour, so the Gmail account is never hammered |

Anything over the limit returns **429** with a `retryAfter` value the UI uses to run a countdown.

**Timing.** A login for an unknown address still runs a BCrypt comparison against a dummy hash, so an
attacker cannot tell a missing account from a wrong password by response time.

### 3.2 Products and stock

`Product` holds name, category, price, image, description and `stock`, plus two denormalised rating
columns (see [3.8](#38-reviews-and-ratings)).

`StockService` is the only place stock moves:

| Operation | Used by |
|---|---|
| `requireAvailable(email)` | before checkout - fails fast if anything in the cart is short |
| `lock(items)` | `SELECT … FOR UPDATE` on every product in the order, **ordered by id** |
| `shortages(items, locked)` | which lines cannot be met, and by how much |
| `take(...)` | decrements stock inside the locked transaction |
| `putBack(orderItems)` | restocks on cancellation |
| `setStock` / `addStock` | admin |
| `lowStock` / `countLowStock` | alerts, threshold **5**, cap **100 000** |

`Product` is annotated `@DynamicUpdate` so an admin editing a name or price writes only the changed
columns. Without it, a stale `Product` loaded a second earlier would overwrite `stock`, `ratingCount`
and `ratingTotal` with old values.

Locking always happens in the same order - **product rows first, then order / review rows** - which
is what keeps concurrent checkouts, cancellations and reviews deadlock-free.

### 3.3 Cart, address and checkout

Cart lines are per user, keyed by product, and the cart refuses to hold more than the current stock.

Every order now carries the address it must be delivered to, stored **on the order** rather than on
the user, so a later profile edit never rewrites delivery history:
`delivery_name`, `delivery_phone`, `delivery_address`, `delivery_city`, `delivery_pincode`.

`util/Addresses.require(...)` is the single validator, shared by both payment paths. It trims and
collapses whitespace, normalises the phone (strips `+91` and a leading `0`), and enforces
`^[6-9]\d{9}$` for the phone and `^\d{6}$` for the pincode, returning a 400 that names the bad field.

The address is validated **before** anything is charged:

- `POST /api/payment/create-order` validates first, so a bad address never reaches Razorpay and
  nobody is ever charged for an order that cannot be placed.
- `OrderService` re-validates as "gate 0" before consuming the payment, because the API is public.

The React checkout pre-fills from the customer's most recent order, and `/payment` redirects back to
`/checkout` if there is no address in hand.

### 3.4 Payments

Two ways to pay, both landing in the same `Order`.

**Razorpay**

1. `POST /api/payment/create-order` - validates the address, prices the cart server-side, creates a
   Razorpay order, persists a `PaymentOrder` row, returns the order id and key id.
2. The browser opens Razorpay Checkout.
3. `POST /api/orders/place` - the server verifies `razorpay_signature` with HMAC-SHA256 over
   `orderId|paymentId` using the secret, marks the `PaymentOrder` used (so a payment cannot be
   replayed into two orders), locks stock, writes the `Order` and its items, clears the cart, mails
   the confirmation.

The amount is **never** taken from the client. It is recomputed from the cart on the server.

**Wallet**

`POST /api/orders/place-with-wallet` debits the wallet with a conditional `UPDATE … WHERE balance >= ?`,
so two concurrent requests can never both succeed against the same balance.

**The out-of-stock-after-payment case.** If someone wins the race for the last unit between paying
and the order being written, `OrderService` does not simply fail: it credits the paid amount to the
customer's wallet and throws `StockRefundedException`, which the handler turns into a **409** saying
the money is already back in the wallet. The credit survives the failure because the method is
annotated `@Transactional(noRollbackFor = StockRefundedException.class)`.

### 3.5 Order lifecycle

```
PLACED ──► CONFIRMED ──► OUT_FOR_DELIVERY ──► DELIVERED
   │            │                │
   └────────────┴────────────────┴──────────► CANCELLED
```

`OrderStatus` owns the rules: `isOpen()` and `canAdvanceTo(next)`. Transitions are **forward only** -
an order can never go back a step, and nothing can move out of `DELIVERED` or `CANCELLED`.

- **Customer cancel** - `POST /api/orders/{id}/cancel`, allowed while the order is open. Locks the
  order row (`findForUpdate`), refunds the **full amount to the wallet** as a `REFUND` transaction
  with reference `ORDER-<id>`, restocks every line, and mails the customer.
- **Admin advance** - `POST /api/admin/orders/{id}/status` moves one step forward and mails the
  customer on each step.
- **Admin cancel** - `POST /api/admin/orders/{id}/cancel` with an optional reason, same refund and
  restock path.
- **Admin list** - `GET /api/admin/orders?status=&page=&size=`, newest first, paged.
- **Stats** - `GET /api/admin/orders/stats` returns counts per status, the open total, and how many
  products are below the low-stock threshold.

Refunds go to the **wallet**, not back through Razorpay. That is deliberate: the money is usable
immediately, it needs no payout reconciliation, and it keeps the customer in the ecosystem. The
wallet ledger records every refund with its order reference, so it is fully auditable.

Both cancel paths take the order row lock **after** the product locks, so an admin cancelling while a
customer cancels the same order cannot deadlock, and only one of them wins.

### 3.6 Wallet

A prepaid balance per user, in **paise**, with a full double-entry style ledger.

- `Wallet` - one row per user, unique on `user_email`.
- `WalletTransaction` - type (`CREDIT` / `DEBIT`), source (`TOPUP`, `ORDER`, `SUBSCRIPTION`,
  `REFUND`, `ADJUSTMENT`), amount, balance after, reference, timestamp, indexed on
  `(user_email, created_at)`.

Top-ups go through Razorpay and are verified exactly like an order payment, and the verification is
replay-safe: the same payment id can never credit twice. Debits use a conditional update so a
concurrent double-spend is impossible.

`WalletForecast` tells the customer roughly how many more days their subscriptions are funded for,
which is what drives the "top up soon" nudge in the UI.

### 3.7 Subscriptions

The largest module. A subscription is a product, a quantity, a frequency, a delivery slot and a start
date; deliveries are generated ahead of time and billed from the wallet.

- **Frequencies** - daily, alternate days, weekly, and custom weekdays. All date maths lives in
  `SubscriptionSchedule`, which is pure and unit-tested (`SubscriptionScheduleTest`).
- **Slots** - `DeliverySlot` (morning / evening), with a daily cut-off hour
  (`app.subscription.cutoff-hour`) after which today is no longer changeable.
- **Generation** - `SubscriptionEngine` runs on `app.subscription.generation-cron` and writes the
  next `SubscriptionDelivery` per active subscription. Each run is recorded in
  `subscription_generation_runs` with a unique constraint on the delivery date, so a double run
  cannot double-charge.
- **Billing** - `SubscriptionDeliveryProcessor` debits the wallet for each delivery. Insufficient
  balance marks the delivery as failed and mails the customer rather than silently dropping it.
- **Customer controls** - pause, resume, cancel, skip a specific date, and a vacation window with a
  start and end. Skips live in `subscription_skips`.
- **Previews** - `POST /api/subscriptions/preview` returns the next dates and the cost before the
  customer commits, and `GET /api/subscriptions/{id}` returns a calendar of upcoming days.
- **Admin** - today's dispatch sheet, mark a delivery delivered, refund a delivery to the wallet, and
  a manual generation trigger.

Full details in [SUBSCRIPTIONS.md](SUBSCRIPTIONS.md).

### 3.8 Reviews and ratings

**Only verified buyers can review.** `ReviewService.eligibility(...)` says a customer may review a
product when they have either a **delivered order** containing it, or a **delivered subscription
delivery** of it. The response carries the reason and the existing review if there is one, so the UI
can show the right state without a second call.

- One review per `(product, customer)`, enforced by the unique key `uk_review_product_user`.
- Rating 1-5 required; title ≤ 80 chars, body ≤ 1000, admin reply ≤ 500. Text is trimmed and
  collapsed before storing.
- The author is shown as a short name - "Alice S." - never the email, and the badge says
  *Verified buyer* or *Subscriber* depending on how the product was received.
- Customers can edit or delete their own review at any time.

**Rating rollups.** `Product` keeps `ratingCount` and `ratingTotal` (the sum of stars), maintained
inside the product row lock every time a review is written, edited, deleted, hidden or shown, so the
average never drifts and never needs a `GROUP BY` at read time. `getRatingAverage()` derives the
displayed value; `ratingTotal` is `@JsonIgnore`, `ratingCount` is read-only over the API.

**Moderation.** An admin can post a public reply (the customer gets an email), or hide a review with
an admin-only reason. A hidden review disappears from the product page **and** is removed from the
average, and can be restored at any time. The moderation desk filters by All / 1-2 stars / Not
replied / Hidden with live counts.

The public listing supports sorting (newest, highest, lowest, most helpful order) and filtering by
star bucket, and returns the 1-5 star distribution alongside the page of reviews.

### 3.9 Sales analytics

`GET /api/admin/analytics/sales?days=7..365` builds the whole dashboard in one request:

- **Totals** for the period and for the **previous period of the same length**, so every headline
  number carries a change: revenue split into cart and subscription, order count, delivery count,
  average order value, cancellations, refunds, buyers and new buyers.
- **Daily series** - cart revenue, subscription revenue, total, orders and deliveries per day.
- **Top products** - units and revenue, split into cart units and subscription units.
- **Plans** - deliveries, revenue and active subscription count per frequency.
- **Customers** - buyers, new, returning and a repeat-rate percentage, computed from a
  first-purchase-date map so "new" means genuinely first-time, not first-in-window.
- **Payments** - online vs wallet vs subscription revenue mix.
- **Subscriptions snapshot** - active, paused, monthly recurring revenue and total wallet float.

It reads orders and subscription deliveries once each and aggregates in Java, so the dashboard is a
single round trip regardless of how many charts the UI draws. The frontend renders it with
hand-built SVG charts - no chart library.

### 3.10 Email

`EmailService` sends every transactional message: OTP, password reset, order confirmation, each
order status change, order cancellation and refund, subscription events, failed subscription
billing, contact-form messages and admin replies to reviews. Mail that is not on the critical path
is sent asynchronously so a slow SMTP server never makes a request hang.

---

## 4. API reference

`Authorization: Bearer <jwt>` unless marked public. All money in responses is rupees.

### Auth - `/api/auth` (public)

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/signup` | `SignupRequest` | creates a disabled user, mails an OTP |
| POST | `/verify` | `VerifyOtpRequest` | 5 attempts, then the code is burnt |
| POST | `/login` | `LoginRequest` | returns `{token, role, …}`; 429 when locked |
| POST | `/resend-otp` | `{email}` | rate-limited |
| POST | `/forgot-password` | `{email}` | always a generic 200 |
| POST | `/reset-password` | `ResetPasswordRequest` | bumps `tokenVersion`, returns a fresh login |

### Products - `/api/products`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/` | public | paged, filterable |
| GET | `/{category}` | public | by category |
| GET | `/{id}/details` | public | product + rating summary |
| GET | `/{id}/reviews?sort=&stars=&page=&size=` | public | page + 1-5 distribution |
| POST | `/` | admin | create |
| PUT | `/{id}` | admin | edit (dynamic update) |
| DELETE | `/{id}` | admin | delete |
| GET | `/api/trending-products` | public | home page picks |

### Cart - `/api/cart`

| Method | Path |
|---|---|
| GET | `/` |
| POST | `/add` |
| PUT | `/update` |
| DELETE | `/remove/{id}` |

### Payments and orders

| Method | Path | Notes |
|---|---|---|
| POST | `/api/payment/create-order` | body `CheckoutRequest{address}`, validates before Razorpay |
| POST | `/api/orders/place` | verifies the signature, locks stock, writes the order |
| POST | `/api/orders/place-with-wallet` | conditional debit, same gates |
| GET | `/api/orders/my-orders` | newest first, items eagerly loaded |
| POST | `/api/orders/{id}/cancel` | open orders only, refunds to the wallet, restocks |

### Wallet - `/api/wallet`

| Method | Path |
|---|---|
| GET | `/` |
| GET | `/transactions` |
| POST | `/topup` |
| POST | `/topup/verify` |

### Subscriptions - `/api/subscriptions`

| Method | Path | Notes |
|---|---|---|
| GET | `/plans` | frequencies and slots |
| POST | `/preview` | dates and cost before committing |
| GET | `/` | mine |
| POST | `/` | create |
| GET | `/{id}` | detail + upcoming calendar |
| PUT | `/{id}` | edit |
| POST | `/{id}/pause` · `/resume` · `/cancel` | state |
| PUT / DELETE | `/{id}/vacation` | set / clear a window |
| POST | `/{id}/skips` · DELETE `/{id}/skips/{date}` | skip a date |
| GET | `/deliveries` | my deliveries |

### Reviews - `/api/reviews`

| Method | Path | Notes |
|---|---|---|
| GET | `/eligibility?productId=` | may I review, and my existing review |
| GET | `/mine` | all my reviews |
| POST | `/` | `ReviewRequest{productId, rating, title, body}`, create or edit |
| DELETE | `/{id}` | mine only |

### Admin - `/api/admin/**` (`ROLE_ADMIN`)

| Method | Path | Notes |
|---|---|---|
| GET | `/orders?status=&page=&size=` | paged, newest first |
| GET | `/orders/stats` | counts per status + low-stock count |
| POST | `/orders/{id}/status` | advance one step |
| POST | `/orders/{id}/cancel` | refund + restock |
| GET | `/products/stock-alerts` | below threshold |
| PUT | `/products/{id}/stock` | set |
| POST | `/products/{id}/restock` | add |
| GET | `/subscriptions/stats` · `/dispatch` | admin views |
| POST | `/subscriptions/deliveries/{id}/delivered` · `/refund` | per delivery |
| POST | `/subscriptions/run` | force a generation run |
| GET | `/reviews?filter=ALL\|LOW\|UNANSWERED\|HIDDEN&page=&size=` | moderation list |
| GET | `/reviews/stats` | published, hidden, average, low, unanswered |
| POST | `/reviews/{id}/hide` · `/show` | with an admin-only reason |
| PUT | `/reviews/{id}/reply` | public reply, empty body removes it |
| GET | `/analytics/sales?days=7..365` | the whole dashboard |

### Misc

| Method | Path |
|---|---|
| POST | `/api/contact` |
| GET | `/test` |

---

## 5. Data model

Schema is managed by Hibernate (`ddl-auto=update`).

| Table | Holds |
|---|---|
| `users` | name, email, BCrypt password, role, enabled, OTP hash + expiry + attempts, lock state, `token_version` |
| `products` | name, category, price, image, description, `stock`, `rating_count`, `rating_total` |
| `cart_items` | one row per user + product |
| `orders` | user, total, `created_at` (IST), `status`, payment method, and the five `delivery_*` columns |
| `order_item` | product id, name and price **snapshotted** at purchase time, quantity |
| `payment_orders` | Razorpay order id, amount, state - the replay guard |
| `wallets` | balance in paise, unique per user |
| `wallet_transactions` | type, source, amount, balance after, reference, indexed `(user_email, created_at)` |
| `subscriptions` | product, quantity, frequency, weekdays, slot, status, vacation window |
| `subscription_deliveries` | one row per generated delivery: date, slot, amount, status |
| `subscription_skips` | a skipped date on a subscription |
| `subscription_generation_runs` | one row per generated date, unique on `delivery_date` |
| `product_reviews` | rating, title, body, `verified_via`, status, hidden reason, admin reply, timestamps; unique `(product_id, user_email)` |

Order items snapshot the product name and price, so renaming or repricing a product never rewrites
what a customer actually paid.

---

## 6. Money, time and concurrency rules

**Money.** Stored and computed as `long` paise through `util/Money`. `BigDecimal` with `HALF_UP`
rounding is used only at the edges, when converting to or from rupees. No `double` anywhere near a
price.

**Time.** `DeliveryCalendar` exposes a `Clock` fixed to `app.timezone` (IST). Nothing calls
`LocalDate.now()` directly - every timestamp, cut-off and "today" comes from the calendar, so the
scheduler behaves identically on a UTC server and a local machine. Order `createdAt` is set from it
too, which is why the analytics day buckets line up with the dairy's actual day.

**Concurrency.** The patterns used throughout:

| Pattern | Where |
|---|---|
| `SELECT … FOR UPDATE` row locks | `ProductRepository.lockAll/lockOne`, `OrderRepository.findForUpdate`, `ReviewRepository.findForUpdate` |
| Fixed lock order (product → order / review) | every path that touches both |
| Conditional `UPDATE … WHERE balance >= ?` | wallet debits |
| Unique constraints as the last line of defence | wallet per user, one review per product+customer, one generation run per date |
| `@Transactional(noRollbackFor = …)` | commit a side effect while still returning an error |
| `@DynamicUpdate` | stop a stale entity clobbering columns it never read |

These are exercised by the test suites: three buyers racing for one unit, eight concurrent reviewers
on one product, six concurrent cancellations of the same order, an admin cancelling while the
customer cancels, and eight concurrent replays of the same top-up payment.

---

## 7. Configuration

`src/main/resources/application.properties` reads everything from environment variables.
`application.properties.example` lists them; copy it and fill in your own values.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | HMAC signing key, at least 256 bits |
| `JWT_EXPIRATION_MS` | token lifetime |
| `RAZORPAY_KEY`, `RAZORPAY_SECRET` | Razorpay API credentials |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail address and **app password** |
| `CONTACT_RECIPIENT` | where the contact form lands |
| `CORS_ALLOWED_ORIGINS` | comma-separated frontend origins |

Application-level settings, all with sensible defaults:

| Property | Default | Meaning |
|---|---|---|
| `app.timezone` | `Asia/Kolkata` | the clock everything uses |
| `app.subscription.cutoff-hour` | - | after this hour today's delivery is locked |
| `app.subscription.generation-cron` | - | when the generator runs |
| `app.auth.code-cooldown-seconds` | `60` | minimum gap between OTP / reset emails |
| `app.cors.allowed-origins` | - | from `CORS_ALLOWED_ORIGINS` |
| `app.contact.recipient` | - | from `CONTACT_RECIPIENT` |

Profiles: `application-docker.properties` and `application-render.properties` for the two deploy
targets.

> **Never commit real credentials.** `intellij-env.txt` and any filled-in properties file stay out of
> git. See [SECURITY-SETUP.md](SECURITY-SETUP.md) for the full checklist, including rotating keys
> that were ever exposed.

## 8. Running locally

```bash
# 1. configure
cp src/main/resources/application.properties.example src/main/resources/application.properties
#    then export the variables above, or set them in your IDE run configuration

# 2. run
./mvnw spring-boot:run          # http://localhost:8080

# 3. build
./mvnw clean package            # target/*.jar
java -jar target/*.jar
```

MySQL 8 must be reachable at `DB_URL`; Hibernate creates and updates the schema on first boot.
The frontend expects the API at `VITE_API_URL` - point it at `http://localhost:8080`.

## 9. Deployment

- **Docker** - the included `Dockerfile` builds the fat JAR and runs it with the `docker` profile.
- **Render** - the `render` profile; set every variable above in the dashboard.
- **Frontend** - built with Vite and served by Nginx on AWS EC2, with `CORS_ALLOWED_ORIGINS`
  pointing at that origin.

## 10. Testing

- `SubscriptionScheduleTest` - JUnit, covers the date maths for every frequency.
- **End-to-end API suites** - **316 assertions** across six suites, all green: delivery addresses
  (32), order lifecycle and stock (66), auth hardening (47), sales analytics (29), reviews (65), and
  the original checkout, wallet and subscription flows (77). They run against a real boot of the app
  on an H2 file database in MySQL mode with a local SMTP sink, and include the concurrency races
  listed in [section 6](#6-money-time-and-concurrency-rules).
- **Browser runs** - Playwright walks the customer and admin journeys on desktop and at 390 px, with
  console errors treated as failures.

## 11. Further reading

| Doc | What it covers |
|---|---|
| [SUBSCRIPTIONS.md](SUBSCRIPTIONS.md) | the subscription engine, schedules, billing and admin tools in depth |
| [ORDERS.md](ORDERS.md) | order lifecycle, stock rules and refund behaviour |
| [SECURITY-SETUP.md](SECURITY-SETUP.md) | credential handling, what to rotate and how |
| [HELP.md](HELP.md) | Spring Boot starter references |
