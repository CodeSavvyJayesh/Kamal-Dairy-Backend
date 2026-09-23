# Deployment

Backend on **Railway**, frontend on **Vercel**, MySQL either Railway's own plugin or an external
host such as Aiven.

Work through this in order. The two halves depend on each other - the backend needs to know the
frontend's URL for CORS, and the frontend needs the backend's URL - so there is one deliberate
back-and-forth at step 4.

---

## Contents

1. [Before you start](#1-before-you-start)
2. [The database](#2-the-database)
3. [The backend on Railway](#3-the-backend-on-railway)
4. [The frontend on Vercel](#4-the-frontend-on-vercel)
5. [Close the loop](#5-close-the-loop)
6. [First-run checks](#6-first-run-checks)
7. [Make yourself admin](#7-make-yourself-admin)
8. [Troubleshooting](#8-troubleshooting)
9. [What is deliberately not production-grade yet](#9-what-is-deliberately-not-production-grade-yet)

---

## 1. Before you start

**Rotate the leaked credentials first.** `application.properties` and the two profile files were
once committed with real values, so they are in git history. Anyone who has ever cloned this repo
has them. Rotate all of these before the app is reachable from the internet:

| Credential | Where |
|---|---|
| Razorpay key + secret | Razorpay Dashboard → Account & Settings → API Keys → Regenerate |
| Gmail app password | Google Account → Security → 2-Step Verification → App passwords |
| MySQL password | Aiven console, or Railway's MySQL plugin → Variables |
| JWT secret | Generate a new one: `openssl rand -base64 48` |

Details and the history-purge steps are in [SECURITY-SETUP.md](SECURITY-SETUP.md).

**Stay on Razorpay test keys** (`rzp_test_...`) until you have read the "before real money" notes at
the end of this document. Test keys let the whole flow work end to end without moving real money.

**Generate the JWT secret now**, you will need it in step 3:

```bash
openssl rand -base64 48
```

It must be at least 32 characters. The app refuses to start with a shorter one. Changing it later
logs everybody out once, which is harmless.

---

## 2. The database

Either option works. Pick one.

### Option A - Railway's MySQL plugin (simplest)

In your Railway project: **New → Database → Add MySQL**. Railway creates the database and exposes
its connection details as variables on that service. Your backend service can reference them
directly, so no password is ever typed by hand.

Inside the same Railway project the two services talk over the private network, so SSL is not
needed.

### Option B - Aiven or another external MySQL

Keep what you already have. You will need the host, port, database name, username and password from
the provider's console. External connections are over the public internet, so **SSL is required** -
note the different `DB_URL` in the next section.

Either way the schema creates itself on first boot: `ddl-auto=update` builds every table, including
the new `invoice_counters`. Nothing to import.

---

## 3. The backend on Railway

### 3.1 Create the service

1. Railway → **New Project → Deploy from GitHub repo** → pick `kamal-dairy-backend`.
2. Railway reads `railway.json` from the repo, so it will build from the `Dockerfile` and use
   `/api/health` as the health check automatically. Nothing to configure by hand.
3. The first deploy **will fail** until you add the variables below. That is by design - the app
   refuses to start with a missing secret rather than falling back to something insecure.

### 3.2 The variables

Railway service → **Variables** tab → **New Variable** for each. Or use **Raw Editor** and paste the
whole block, which is much faster.

**Required.** The app will not start without every one of these.

| Variable | Value | Where it comes from |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `railway` | fixed - this selects `application-railway.properties` |
| `DB_URL` | see below | your database |
| `DB_USERNAME` | your MySQL user | your database |
| `DB_PASSWORD` | your MySQL password | your database |
| `MAIL_USERNAME` | your Gmail address | Google account |
| `MAIL_PASSWORD` | the 16-character app password | Google → App passwords (**not** your Google password) |
| `RAZORPAY_KEY` | `rzp_test_...` | Razorpay Dashboard → API Keys |
| `RAZORPAY_SECRET` | the matching secret | Razorpay Dashboard → API Keys |
| `JWT_SECRET` | the string from `openssl rand -base64 48` | you generated it in step 1 |
| `CORS_ALLOWED_ORIGINS` | placeholder for now, fixed in step 5 | Vercel, once it exists |

**`DB_URL` with Railway MySQL** - reference the plugin's variables so nothing is copied by hand
(replace `MySQL` with the exact service name if you renamed it):

```
jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=Asia/Kolkata&useSSL=false&allowPublicKeyRetrieval=true
```

and then `DB_USERNAME` = `${{MySQL.MYSQLUSER}}`, `DB_PASSWORD` = `${{MySQL.MYSQLPASSWORD}}`.

> Check the exact variable names in the MySQL service's own Variables tab before you paste. Railway
> has changed these names before, and a typo here shows up as a confusing "communications link
> failure" rather than "unknown variable".

**`DB_URL` with Aiven or another external host** - SSL is not optional over the public internet:

```
jdbc:mysql://HOST:PORT/DATABASE?serverTimezone=Asia/Kolkata&sslMode=REQUIRED
```

**Recommended.** Everything below has a working default, but you want these set.

| Variable | Suggested value | What it does |
|---|---|---|
| `APP_TIMEZONE` | `Asia/Kolkata` | the clock the whole app runs on - orders, invoices, the subscription cut-off |
| `APP_SUBSCRIPTION_CUTOFF_HOUR` | `23` | after this hour, tomorrow's deliveries are locked and billed |
| `CONTACT_RECIPIENT` | where the contact form should land | defaults to `MAIL_USERNAME` |
| `LOG_LEVEL_APP` | `INFO` | your own logs; use `DEBUG` only while chasing something |

**Invoicing.** All optional, but the invoice looks unfinished without them. See
[INVOICING.md](INVOICING.md).

| Variable | Notes |
|---|---|
| `INVOICE_SELLER_NAME` | legal name of the business |
| `INVOICE_TRADE_NAME` | the name shown large at the top |
| `INVOICE_GSTIN` | **leave blank unless the dairy is actually GST registered.** Blank issues a bill of supply, which is the correct document for an unregistered seller. Setting it makes the app issue tax invoices, which are legal documents |
| `INVOICE_FSSAI` | FSSAI licence number |
| `INVOICE_ADDRESS_LINE1`, `INVOICE_ADDRESS_LINE2` | street address |
| `INVOICE_CITY`, `INVOICE_STATE`, `INVOICE_PINCODE` | |
| `INVOICE_PHONE`, `INVOICE_EMAIL` | contact printed on the invoice |
| `INVOICE_PREFIX` | leading segment of the number, default `KD` → `KD/2026-27/000042` |
| `INVOICE_SIGNATORY` | name under the signature line |
| `INVOICE_TERMS` | one line under the declaration |

**Do not set `PORT`.** Railway injects it and the app binds to it. Overriding it makes the deploy
look healthy in the logs and unreachable from the internet.

### 3.3 Paste-ready block

Railway's Raw Editor accepts this. Fill in the right-hand sides.

```
SPRING_PROFILES_ACTIVE=railway
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=Asia/Kolkata&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
MAIL_USERNAME=
MAIL_PASSWORD=
RAZORPAY_KEY=
RAZORPAY_SECRET=
JWT_SECRET=
CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_TIMEZONE=Asia/Kolkata
APP_SUBSCRIPTION_CUTOFF_HOUR=23
LOG_LEVEL_APP=INFO
INVOICE_SELLER_NAME=Kamal Dairy
INVOICE_TRADE_NAME=Kamal Dairy
INVOICE_GSTIN=
INVOICE_FSSAI=
INVOICE_ADDRESS_LINE1=
INVOICE_CITY=Mumbai
INVOICE_STATE=Maharashtra
INVOICE_PINCODE=
INVOICE_PHONE=
INVOICE_EMAIL=
INVOICE_PREFIX=KD
INVOICE_SIGNATORY=
```

### 3.4 Deploy and get the URL

Redeploy. Watch the logs for `Started KamalDairyBackendApplication`. Then **Settings → Networking →
Generate Domain** to get a public URL like `https://kamal-dairy-backend-production.up.railway.app`.

Check it:

```bash
curl https://YOUR-BACKEND.up.railway.app/api/health
```

Expect `{"status":"UP","database":"UP","time":"..."}`. If `database` says `DOWN`, the app is running
but cannot reach MySQL - check `DB_URL`.

---

## 4. The frontend on Vercel

1. Vercel → **Add New → Project** → import `Kamal-Dairy`.
2. Vercel reads `vercel.json`, so framework, build command and output directory are already right.
   The SPA rewrite in that file is what makes `/orders` work on a hard refresh instead of 404ing.
3. **Environment Variables** → add one, for all three environments (Production, Preview,
   Development):

| Variable | Value |
|---|---|
| `VITE_API_URL` | `https://YOUR-BACKEND.up.railway.app` - no trailing slash |

4. Deploy. Note the URL, e.g. `https://kamal-dairy.vercel.app`.

> `VITE_` variables are **inlined into the bundle at build time**, so anyone can read them in
> DevTools. That is fine for the API URL. Never put a secret in a `VITE_` variable. It also means
> changing `VITE_API_URL` requires a **redeploy**, not just a restart.

---

## 5. Close the loop

Go back to Railway and set `CORS_ALLOWED_ORIGINS` properly:

```
CORS_ALLOWED_ORIGINS=https://kamal-dairy.vercel.app,https://*.vercel.app,https://kamaldairy.online
```

- the first is your production frontend
- the second lets **Vercel preview deployments** work - every branch and pull request gets its own
  hostname, and without the wildcard every preview is blocked by CORS
- the third is your custom domain, if you have one

Wildcards are allowed because the backend matches origins as patterns. It is still an allowlist -
never set it to `*`. With credentials enabled a bare `*` is rejected by browsers anyway.

Redeploy the backend for the change to take effect.

---

## 6. First-run checks

Work down this list. Each one catches a different class of mistake.

| # | Check | Expected |
|---|---|---|
| 1 | `curl https://BACKEND/api/health` | `{"status":"UP","database":"UP",...}` |
| 2 | Open the Vercel site, hard refresh on `/products` | loads, no 404 - proves the SPA rewrite works |
| 3 | Browser DevTools → Network, with the site open | product requests return 200, no CORS errors |
| 4 | Sign up with a real address | OTP email arrives - proves Gmail credentials work |
| 5 | Log in | you land signed in |
| 6 | Place a wallet order end to end | order appears under My Orders |
| 7 | Admin → mark it delivered | delivery email arrives **with the invoice PDF attached** |
| 8 | Download the invoice from My Orders | numbered `KD/2026-27/000001` |
| 9 | Railway logs | no stack traces, no `WARN` about mail |

If the `time` in the health response is not IST, `APP_TIMEZONE` did not take effect - the
subscription cut-off and the invoice dates depend on it.

---

## 7. Make yourself admin

Roles are assigned by the server and can never come from a signup payload, so promote your account
directly in the database. Railway MySQL → **Data** tab (or connect with any MySQL client):

```sql
UPDATE users SET role = 'ROLE_ADMIN' WHERE email = 'your@email.com';
```

Log out and back in - the frontend caches the role from the login response.

---

## 8. Troubleshooting

**Deploy fails with `Could not resolve placeholder 'X'`.** That variable is missing. This is the app
refusing to start half-configured, which is what you want. Add it and redeploy.

**`Communications link failure` or `Access denied`.** `DB_URL`, `DB_USERNAME` or `DB_PASSWORD` is
wrong. With Railway MySQL, check the plugin's variable names match exactly what you referenced.
With an external host, confirm `sslMode=REQUIRED` is in the URL.

**The health check passes but the site shows CORS errors.** `CORS_ALLOWED_ORIGINS` does not include
the exact origin the browser is using. It must include the scheme and no trailing slash:
`https://kamal-dairy.vercel.app`, not `kamal-dairy.vercel.app/`.

**Preview deployments are blocked but production works.** Add `https://*.vercel.app` to
`CORS_ALLOWED_ORIGINS`.

**`/orders` 404s on refresh but works when clicked.** The SPA rewrite is not applying - confirm
`vercel.json` is committed at the repo root.

**No emails.** Gmail needs an **app password**, not your account password, and 2-Step Verification
must be on. Gmail also caps around 500 messages a day and will suspend an account that looks like a
bulk sender - move to Brevo, Resend or SES before real volume.

**Subscriptions are not generating.** The generator runs on a schedule inside the app, so the
container has to be awake at the cut-off hour. Railway's Hobby plan does not sleep; if you are on a
plan that does, deliveries will silently not be created. Check the logs around the cut-off hour.

**Deploys are slow.** The Docker build is two-stage and caches dependencies in their own layer, so
only the first build is slow. If every build is slow, something is invalidating the cache - usually
an edit to `pom.xml`.

---

## 9. What is deliberately not production-grade yet

Worth knowing, and worth saying out loud in an interview rather than being caught by it.

- **`ddl-auto=update` manages the schema.** Fine for one developer and additive changes, which is
  all this app has needed. It cannot rename or drop safely - a renamed field silently becomes a new
  column and the old data is orphaned. Flyway is the fix when the schema stops being append-only.
- **Rate limiting and lockouts are in-memory.** `AuthGuard` holds them in a map, so they reset on
  every restart and do not work across replicas. `railway.json` pins `numReplicas: 1` partly for
  this reason. Redis is the fix if it ever scales out.
- **Refunds go to the wallet, not back to the card.** Deliberate - instant for the customer, no
  payout reconciliation - but it needs to be stated in your terms, because a customer who paid by
  card may expect refund-to-source.
- **The wallet holds customer money.** A prepaid balance is a stored-value instrument, and in India
  RBI has rules about who may issue one. Worth a conversation with a CA before you take real money.
- **Tax invoices are legal documents.** Only set `INVOICE_GSTIN` if the business is genuinely
  registered. Blank is not a degraded mode - it is the correct document for an unregistered seller.
- **No automated backups configured here.** Whichever database you chose, turn its backups on.
