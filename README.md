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
| GET | `/api/admin/owners/pending` | admin | Owners waiting for approval |
| POST | `/api/admin/users/{id}/approve` | admin | Approve an owner |

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format.

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
