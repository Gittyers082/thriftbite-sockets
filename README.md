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

**Frontend**
```bash
cd frontend
npm install
npm run dev
```

**Tests**
```bash
cd backend
./mvnw verify                 # needs Docker for Testcontainers
```

## API

| Method | Endpoint | Who | What it does |
|---|---|---|---|
| POST | `/api/auth/register` | anyone | Sign up as `OWNER` or `CONSUMER` (owners need approval) |
| POST | `/api/auth/login` | anyone | Returns a JWT to send as `Authorization: Bearer <token>` |
| GET | `/api/auth/me` | logged in | The current user |
| GET | `/api/items?q=&page=&size=` | anyone | Browse food for sale, soonest-expiring first |
| GET | `/api/items/{id}` | anyone | One listing |
| GET | `/api/owner/items` | owner | Your own listings |
| POST | `/api/owner/items` | owner | Create a listing |
| PUT | `/api/owner/items/{id}` | owner | Edit a listing (send the `version` you loaded) |
| DELETE | `/api/owner/items/{id}` | owner | Remove a listing |
| GET | `/api/cart` | consumer | Your cart, with a warning on lines that sold out |
| PUT | `/api/cart/items/{itemId}` | consumer | Add an item or change its quantity (`0` removes it) |
| POST | `/api/checkout` | consumer | Reserve everything in the cart, all or nothing: one reservation and claim code per store |
| GET | `/api/reservations` | consumer | Your reservations and claim codes |
| POST | `/api/reservations/{id}/cancel` | consumer | Cancel before pickup (stock goes back on sale) |
| GET | `/api/owner/reservations` | owner | Pickups waiting at your store |
| POST | `/api/owner/reservations/{id}/complete` | owner | Hand over the food after checking the buyer's claim code |
| GET | `/api/admin/owners/pending` | admin | Owners waiting for approval |
| POST | `/api/admin/users/{id}/approve` | admin | Approve an owner |
| GET | `/api/demo` | anyone | Demo accounts and when the demo data next resets (demo deployments only) |
| POST | `/api/demo/login` | anyone | Log in as the demo `BUYER`, `OWNER` or `ADMIN` (demo deployments only) |

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format, with a `code` field where the frontend needs to tell cases apart (`rate_limited`, `login_locked`, `claim_code_locked`, `account_not_approved`).

### Limits

Reserving is free (buyers pay at the counter), so the API limits abuse:

- A buyer holds at most **2 reservations** at a time.
- **5 wrong claim codes** on one reservation pause code entry for 15 minutes.
- **5 wrong passwords** in a row pause that username for 15 minutes.
- Per-address limits on log in, sign-up, checkout and claim-code entry return `429` with `Retry-After`.

## Deploy

The live demo runs on free tiers: **Neon** (Postgres, Singapore), **Render** (the API in Docker, Singapore) and **Vercel** (the frontend).

1. **Neon:** create a project in the AWS Singapore region. From *Connect*, copy the Java (JDBC) URL, the user and the password.
2. **Render:** *New > Blueprint*, pick this repo. [`render.yaml`](render.yaml) defines the service; Render asks for the database URL, user and password, an admin username and password (12+ characters), and the frontend's URL for CORS. It generates the JWT secret itself. It deploys from `main`, and only after CI passes.
3. **Vercel:** import the repo with root directory `frontend` and set `VITE_API_URL` to the Render URL (for example `https://thriftbite-api.onrender.com`).
4. Put the Vercel URL in Render's `APP_CORS_ALLOWED_ORIGINS`.

Notes on the free tiers:

- Render's free service sleeps after 15 minutes without traffic, and the first request after that takes about a minute while Java starts. An uptime monitor pinging `/actuator/health/liveness` every 10 minutes keeps it awake. That check doesn't touch the database, so Neon can still sleep.
- Neon's free compute sleeps after 5 minutes without queries. The connection pool empties when idle and the expiry job runs every 30 minutes, so a quiet demo uses few compute hours.
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
