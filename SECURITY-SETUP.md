# Security setup

The five critical issues found in the audit are fixed in code. Three of them
also need an action from you, because code alone cannot un-leak a credential
or re-run a deployment.

---

## 1. Rotate the leaked credentials (do this first)

`application.properties`, `application-docker.properties` and
`application-render.properties` were committed with real values. They are still
readable in the git history of this repository, so anyone who has ever had a
copy of this repo has them.

Rotate all four:

| Credential | Where to rotate |
|---|---|
| Razorpay key + secret | Razorpay Dashboard > Account & Settings > API Keys > Regenerate |
| Gmail app password | Google Account > Security > 2-Step Verification > App passwords > delete the old one, create a new one |
| MySQL / Aiven password | Aiven console > your service > Users > reset password |
| JWT signing secret | Already done: a fresh one was generated into `.env.local`. Rotating it logs everyone out once, which is intended. |

The old Razorpay secret in particular must be revoked. It is what signs and
verifies payments.

---

## 2. Supply the values as environment variables

No secret is read from a committed file any more. Every one comes from the
environment, and the app fails to start if one is missing.

Local development values live in `.env.local`, which is gitignored. It was
generated from your previous `application.properties`, so nothing was lost -
but every value in it needs replacing with a rotated one.

**IntelliJ:** Run > Edit Configurations > select the app > Environment variables
> paste the contents of `.env.local` as `KEY=value;KEY=value`. Or install the
EnvFile plugin and point it at `.env.local`.

**Render:** Dashboard > your service > Environment. Set:

```
SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD
RAZORPAY_KEY, RAZORPAY_SECRET
JWT_SECRET
CORS_ALLOWED_ORIGINS=https://kamal-dairy-ten.vercel.app,https://kamaldairy.online
```

**Docker:** pass them with `-e` or an `--env-file`.

`src/main/resources/application.properties.example` lists every variable.

---

## 3. Purge the secrets from git history

Rotating makes the old values worthless, which is the important part. Removing
them from history is the cleanup. This rewrites history, so coordinate it if
anyone else has a clone.

```bash
pip install git-filter-repo

cd kamal-dairy-backend
git filter-repo --invert-paths \
  --path src/main/resources/application.properties \
  --path src/main/resources/application-docker.properties \
  --path src/main/resources/application-render.properties

# re-add the sanitised versions
git add src/main/resources/application*.properties
git commit -m "Add environment-driven configuration"

git push --force --all
git push --force --tags
```

The files are safe to keep tracked now: they contain only `${VAR}` references,
and Render builds from `application-render.properties`.

---

## 4. Make yourself an admin

Roles are assigned by the server and can never come from a signup payload.
Promote your own account directly in MySQL:

```sql
UPDATE users SET role = 'ROLE_ADMIN' WHERE email = 'your@email.com';
```

Log out and back in afterwards - the role is read from the database on every
request, so the change takes effect immediately, but the frontend caches the
role from the login response.

---

## 5. First run after this change

`spring.jpa.hibernate.ddl-auto=update` creates the new `payment_orders` table
and adds `razorpay_order_id`, `razorpay_payment_id` and `created_at` to
`orders` automatically. No manual migration is needed.

Existing orders will show no date, which is expected - they predate the column.

---

## What changed in the code

**Payment verification.** `/api/payment/create-order` no longer takes an amount
and is no longer public. It computes the total from your cart and the products
table, and records what it asked Razorpay to charge. `/api/orders/place` now
requires the Razorpay order id, payment id and signature, and checks: we issued
this payment order, it belongs to you, the HMAC signature verifies against our
secret, it has not already been used, and the cart total still matches the
amount charged. Previously a bare authenticated POST created a free order.

**Server-side pricing.** `/api/cart/add` takes only `productId` and `quantity`.
Name and price come from the database, and are refreshed on every cart read and
again when the order is placed.

**Cart ownership.** Update and remove verify the item belongs to the caller
before touching it.

**Admin separation.** Product create/update/delete require `ROLE_ADMIN`, both
via `@PreAuthorize` and via URL rules in `SecurityConfig`. They used to require
`hasRole('USER')`, which every customer satisfied.

**Secrets.** JWT signing key moved out of `JwtUtil.java` into an environment
variable, with a startup check that it exists and is long enough.

Also cleaned up along the way: the wildcard CORS filter and all
`@CrossOrigin("*")` annotations were removed in favour of one allowlist;
sessions are stateless; auth failures return JSON 401/403 instead of a redirect;
`GlobalExceptionHandler` now maps errors to real status codes instead of
letting everything surface as a 500; page size is clamped; disabled accounts
can no longer use a token issued before they were disabled; and expired tokens
are rejected client-side instead of being sent.

## Not done yet

- Rate limiting on login and OTP. Signup currently allows unlimited OTP emails
  through your Gmail account.
- Password reset. There is still no forgot-password flow.
- Shipping address is collected at checkout and still not persisted.
- Cash on Delivery is removed from the payment page rather than implemented -
  it previously charged customers online while labelled COD.
- Stock is not checked, so an order can exceed available inventory.
