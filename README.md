# ThriftBite

A marketplace for surplus food. Stores list near-expiry food at a discount, and buyers reserve it and pick it up before it goes to waste.

> 🚧 **v2 in progress:** rebuilding the original Java socket project as a full-stack web app.
> The original version is preserved on the [`original-version`](../../tree/original-version) branch (tag `v1.0`).

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Validation, WebSocket) |
| Database | PostgreSQL 17, Flyway migrations |
| Frontend | React, TypeScript, Vite |
| Testing | JUnit 5, Testcontainers |
| CI | GitHub Actions |

## Run it locally

**Requirements:** JDK 21+, Node.js 22+, Docker Desktop (running).

**Backend**: starts Postgres automatically through `backend/compose.yaml`.
```bash
cd backend
./mvnw spring-boot:run        # Windows PowerShell: .\mvnw spring-boot:run
```
The API runs at http://localhost:8080 (health check: `/actuator/health`).

To create an admin account locally, set these before starting (PowerShell):
```powershell
$env:APP_ADMIN_USERNAME = "admin"
$env:APP_ADMIN_PASSWORD = "<at-least-12-characters>"
```

To get the one-tap demo accounts (and a month of sample sales) locally, also set `$env:APP_DEMO_ENABLED = "true"` before starting the API. This wipes the local database's other accounts on startup.

**Frontend**
```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173, talks to the API on :8080
```
The API address comes from `VITE_API_URL` (see [`frontend/.env.example`](frontend/.env.example)); without it the app uses `http://localhost:8080`.

**Tests**
```bash
cd backend
./mvnw verify                 # needs Docker for Testcontainers
```

## API

| Method | Endpoint | Who | What it does |
|---|---|---|---|
| POST | `/api/auth/register` | anyone | Sign up as `OWNER` or `CONSUMER` (owners need approval). Returns the account's one-time recovery codes |
| POST | `/api/auth/recover` | anyone | Forgot password: set a new one with a recovery code (leave `newPassword` out to only check the code) |
| POST | `/api/auth/login` | anyone | Returns a JWT to send as `Authorization: Bearer <token>` |
| GET | `/api/auth/me` | logged in | The current user |
| GET | `/api/account` | logged in | The account page: for buyers, money saved and pickup record |
| POST | `/api/account/password` | logged in | Change the password (logs out every other device) |
| POST | `/api/account/recovery-codes` | logged in | Make a new set of recovery codes (asks for the password again) |
| GET | `/api/account/export` | logged in | Download everything kept about the account, as JSON |
| POST | `/api/account/delete` | consumer | Delete the account (asks for the password again) |
| GET | `/api/items?q=&page=&size=` | anyone | Browse food for sale, soonest-expiring first |
| GET | `/api/items/{id}` | anyone | One listing |
| GET | `/api/stores/{slug}` | anyone | A store's public page: profile, what it has on sale now, and its last 30 days in numbers |
| GET | `/api/owner/items` | owner | Your own listings |
| POST | `/api/owner/items` | owner | Create a listing |
| PUT | `/api/owner/items/{id}` | owner | Edit a listing (send the `version` you loaded) |
| DELETE | `/api/owner/items/{id}` | owner | Remove a listing |
| GET | `/api/owner/items/usual?for=` | owner | "Post again": what the store usually lists and how it went last time, for today or tomorrow |
| POST | `/api/owner/items/batch` | owner | Post several listings with one pickup window, all or nothing |
| GET | `/api/owner/store` | owner | Your store profile |
| PUT | `/api/owner/store` | owner | Save the profile: address, landmark, opening hours, usual pickup window, GCash (send the `version` you loaded) |
| GET | `/api/cart` | consumer | Your cart, with a warning on lines that sold out |
| PUT | `/api/cart/items/{itemId}` | consumer | Add an item or change its quantity (`0` removes it) |
| POST | `/api/checkout` | consumer | Reserve everything in the cart, all or nothing: one reservation and claim code per store |
| GET | `/api/reservations` | consumer | Your reservations and claim codes |
| POST | `/api/reservations/{id}/cancel` | consumer | Cancel before pickup (stock goes back on sale) |
| GET | `/api/owner/reservations` | owner | Pickups waiting at your store |
| POST | `/api/owner/reservations/{id}/complete` | owner | Hand over the food after checking the buyer's claim code |
| GET | `/api/owner/stats?from=&to=` | owner | Sales for your store: money made, share of listed food sold, leftovers by weekday, pickups by hour, per-item results (Manila days; default the last 7 full days) |
| GET | `/api/admin/owners/pending` | admin | Owners waiting for approval |
| POST | `/api/admin/users/{id}/approve` | admin | Approve an owner |
| WS | `/ws` | anyone | Live updates (see below); log in by sending the token as the first message |
| GET | `/api/demo` | anyone | Demo accounts and when the demo data next resets (demo deployments only) |
| POST | `/api/demo/login` | anyone | Log in as the demo `BUYER`, `OWNER` or `ADMIN` (demo deployments only) |

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format, with a `code` field where the frontend needs to tell cases apart (`rate_limited`, `login_locked`, `claim_code_locked`, `account_not_approved`).

### Accounts

ThriftBite never asks for an email or phone number, so there is nothing to send a reset link to. Instead:

- **Recovery codes.** Signing up returns eight one-time codes, shown once. Only their SHA-256 hashes are stored. A code plus a new password resets the account; the check and the "used" mark are one SQL statement, so a code sent twice at the same moment works once.
- **Logging out everywhere.** Each account has a token version that goes up when the password changes. Tokens carry the version they were issued under, and an older one is refused on the next request.
- **Asking again.** Making new codes and deleting the account ask for the password again, so a stolen login can't be turned into a permanent takeover.
- **Your data.** Anyone can download what's kept about them. A buyer can delete their account: anything they're holding goes back on sale, and the account row is scrubbed (no username, no codes, no way to log in) rather than removed, so stores' past sales still add up.
- **The shared demo accounts** can't change any of this.

### Stores

Each store has a public page at `/stores/{slug}` with its address and landmark, opening hours, what it has on sale now, and a short track record. The numbers come from the same stock history the sales page uses: meals picked up in the last 30 days, the share of reservations that were picked up, and the time of day the store usually posts. Where there isn't enough history to say something honest (fewer than 5 finished reservations, fewer than 3 posts), the page says nothing.

**Post again** turns that history into tomorrow's listings. For each food the store has sold in the last five weeks it shows the same weekday last week (a bakery's Sunday is not its Tuesday), with a nudge: more if it was all reserved before pickup opened, fewer if 60% or less was picked up. The owner adjusts the amounts and posts them all in one transaction.

A store that hasn't been approved yet has no public page.

### Live updates

Open screens update on their own over a WebSocket at `/ws`: a stock count drops on every browse page when someone reserves, a new reservation appears on the store's counter screen, and a buyer's ticket flips to "picked up" when the store hands the food over.

- **Sent only after commit.** Services publish a change inside their transaction; it goes out only once that transaction commits (`@TransactionalEventListener`). A checkout that rolls back because something sold out announces nothing.
- **Messages are hints, not data.** Apart from a listing's public stock count, a message only says which part of the screen is out of date (`store`, `reservations`, `deals`, `approvals`), and the screen reloads it through the normal API. Nothing private travels over the socket, and the app still works when the socket is down.
- **Logging in.** A connection starts anonymous and hears public stock changes. The frontend then sends `{"type":"auth","token":"..."}` as its first message; the token is checked exactly like on an API call. It isn't put in the URL, because URLs end up in logs.
- **Staying connected.** The server pings every 25 seconds so proxies keep the connection open. The frontend reconnects with a growing delay, reloads what it may have missed, and lets go of the connection when the tab has been hidden for a minute.
- **One instance.** Connections are kept in memory. With several instances, changes would have to be passed between them (for example with Redis pub/sub).

### Limits

Reserving is free (buyers pay at the counter), so the API limits abuse:

- A buyer holds at most **2 reservations** at a time.
- **5 wrong claim codes** on one reservation pause code entry for 15 minutes.
- **5 wrong passwords** from one address pause that username for that address for 15 minutes; 20 from anywhere pause it for everyone. Counting per address means a stranger who knows a username can't lock its owner out. The same limit applies to recovery codes and to the "current password" box.
- Per-address limits on log in, sign-up, checkout and claim-code entry return `429` with `Retry-After`. Every other API call shares a generous per-address limit (1,200 per 10 minutes), so a script can't flood the API.
- Live connections: 500 at once in total, 60 from one address, and 300 opened per address per 10 minutes.

### Security notes

- **Passwords** are stored as BCrypt hashes. A failed login takes the same time and gives the same message whether or not the username exists.
- **Tokens** are signed JWTs that last 8 hours. The token only says who is asking: the role, approval and token version are read from the database on every request, so deleting an account, changing its role or changing its password takes effect at once.
- **Recovery codes** are 12 random characters, stored only as hashes. A fast hash is enough because nobody chose them (there's no dictionary to try), and guessing against the live site is stopped by the attempt limits. A wrong code and a username that doesn't exist get the same answer.
- **Every lookup is scoped to the caller.** The user id comes from the verified token, never from the request, and a store can only reach its own listings and pickups.
- **Rate limits use the visitor's own address.** Behind Render that is the `CF-Connecting-IP` header (set in `render.yaml`); `X-Forwarded-For` can be faked there.
- **The demo admin is boxed in.** Anyone can log in as it, so it only sees and approves the demo's sample stores.
- **The frontend sends a Content Security Policy** (`frontend/vercel.json`): scripts only from the site itself, API calls and the live connection only to the site and `*.onrender.com`, and no framing by other sites. If the API moves to another domain, update `connect-src` there.
- **The live connection** only accepts pages from the frontend's own address (the CORS list), takes one small login message and closes on anything else, and drops browsers that stop reading.

## Deploy

The live demo runs on free tiers: **Neon** (Postgres, Singapore), **Render** (the API in Docker, Singapore) and **Vercel** (the frontend).

1. **Neon:** create a project in the AWS Singapore region. From *Connect*, copy the Java (JDBC) URL, the user and the password.
2. **Render:** *New > Blueprint*, pick this repo. [`render.yaml`](render.yaml) defines the service; Render asks for the database URL, user and password, an admin username and password (12+ characters), and the frontend's URL for CORS. It generates the JWT secret itself. It deploys from `main`, and only after CI passes.
3. **Vercel:** import the repo with root directory `frontend` and set `VITE_API_URL` to the Render URL (for example `https://thriftbite-api.onrender.com`).
4. Put the Vercel URL in Render's `APP_CORS_ALLOWED_ORIGINS`.

Notes on the free tiers:

- Render's free service sleeps after 15 minutes without traffic, and the first request after that takes about a minute while Java starts. An uptime monitor pinging `/actuator/health/liveness` every 10 minutes keeps it awake. That check doesn't touch the database, so Neon can still sleep.
- Neon's free compute sleeps after 5 minutes without queries. The connection pool empties when idle and the expiry job runs every 30 minutes, so a quiet demo uses few compute hours.
- Render reaches the app through Cloudflare, so `render.yaml` sets `APP_CLIENT_IP_HEADER=CF-Connecting-IP`. On another host, set it to that host's header, or every visitor shares one rate limit.
- `APP_DEMO_ENABLED=true` adds one-tap demo accounts with sample Baguio data, rebuilt every 6 hours. **A rebuild deletes every account except the admin from `APP_ADMIN_USERNAME`**, so only turn it on for a demo deployment.

## Project structure

```
backend/    Spring Boot API
frontend/   React + TypeScript app
.github/    CI workflow
```

## Evolution

| Version | Communication | Storage | Client |
|---|---|---|---|
| v1 (`original-version`) | Raw TCP sockets, thread per client | XML files | Java Swing |
| v2 (this branch) | REST + WebSockets | PostgreSQL | React web app |

A version built on Java RMI lives in **thriftbite-rmi**.

## My role

**v2:** I'm rebuilding the whole project, both backend and frontend, with AI-assisted development.

**v1 (Team 7, university project):** I was the backend developer. I built the socket server, the request handling for all 25 protocol actions, and the XML persistence layer.
