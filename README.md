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
| PUT | `/api/stores/{slug}/follow` | consumer | Follow a store, or turn its alerts on or off (`{"alertsOn": false}`) |
| DELETE | `/api/stores/{slug}/follow` | consumer | Unfollow |
| GET | `/api/follows` | consumer | The stores you follow |
| GET | `/api/alerts` | consumer | Your alerts from the last two weeks, with the unread count |
| GET | `/api/alerts/count` | consumer | Just the unread count, for the number on the nav |
| POST | `/api/alerts/read`, `/api/alerts/{id}/read` | consumer | Mark all, or one, as read |
| GET | `/api/owner/items` | owner | Your own listings |
| POST | `/api/owner/items` | owner | Create a listing |
| PUT | `/api/owner/items/{id}` | owner | Edit a listing (send the `version` you loaded) |
| DELETE | `/api/owner/items/{id}` | owner | Remove a listing |
| GET | `/api/owner/items/usual?for=` | owner | "Post again": what the store usually lists and how it went last time, for today or tomorrow |
| POST | `/api/owner/items/batch` | owner | Post several listings with one pickup window, all or nothing |
| GET | `/api/owner/staff` | owner | The store's staff accounts (the owner only, not staff) |
| POST | `/api/owner/staff` | owner | Add a staff account; returns a temporary password, once |
| POST | `/api/owner/staff/{id}/password` | owner | Give a staff member a new temporary password and log them out |
| DELETE | `/api/owner/staff/{id}` | owner | Remove a staff account |
| POST | `/api/account/close-store` | owner | Close the store for good (needs the owner's password) |
| POST | `/api/owner/photos` | owner | Upload a photo (`multipart/form-data`, field `file`, JPG or PNG up to 6 MB); put the `id` it returns on a listing or the store profile as `photoId` |
| GET | `/api/photos/{id}` | anyone | A photo as a JPEG; add `?size=thumb` for the small one used in lists |
| GET | `/api/owner/store` | owner | Your store profile |
| PUT | `/api/owner/store` | owner | Save the profile: address, landmark, opening hours, usual pickup window, GCash (send the `version` you loaded) |
| GET | `/api/cart` | consumer | Your cart, with a warning on lines that sold out |
| PUT | `/api/cart/items/{itemId}` | consumer | Add an item or change its quantity (`0` removes it) |
| POST | `/api/checkout` | consumer | Reserve everything in the cart, all or nothing: one reservation and claim code per store |
| GET | `/api/reservations` | consumer | Your reservations and claim codes |
| POST | `/api/reservations/{id}/cancel` | consumer | Cancel before pickup (stock goes back on sale) |
| POST | `/api/reports` | consumer | Report a problem with one of your pickups (`reservationId`) or with a store (`storeSlug`) |
| GET | `/api/reports` | consumer | The problems you reported, and the admin's reply once there is one |
| GET | `/api/owner/reservations` | owner | Pickups waiting at your store |
| POST | `/api/owner/reservations/{id}/complete` | owner | Hand over the food after checking the buyer's claim code |
| GET | `/api/owner/stats?from=&to=` | owner | Sales for your store: money made, share of listed food sold, leftovers by weekday, pickups by hour, per-item results (Manila days; default the last 7 full days) |
| GET | `/api/admin/owners/pending` | admin | Owners waiting for approval |
| POST | `/api/admin/users/{id}/approve` | admin | Approve an owner |
| GET | `/api/admin/users?q=&show=` | admin | Buyers and stores, by username or store name; `show` is `buyers`, `stores` or `suspended` |
| POST | `/api/admin/users/{id}/suspend` | admin | Suspend an account, with a reason the account holder is shown |
| POST | `/api/admin/users/{id}/unsuspend` | admin | Lift a suspension |
| GET | `/api/admin/summary` | admin | How many stores and reports are waiting |
| GET | `/api/admin/reports?status=` | admin | Problem reports: `open` (longest wait first) or `resolved` |
| POST | `/api/admin/reports/{id}/resolve` | admin | Reply to the buyer and close the report; `excuseMiss: true` also stops the missed pickup it's about counting against the buyer |
| GET | `/api/admin/activity` | admin | What admins did, newest first |
| WS | `/ws` | anyone | Live updates (see below); log in by sending the token as the first message |
| GET | `/api/demo` | anyone | Demo accounts and when the demo data next resets (demo deployments only) |
| POST | `/api/demo/login` | anyone | Log in as the demo `BUYER`, `OWNER` or `ADMIN` (demo deployments only) |

Errors use the standard [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON format, with a `code` field where the frontend needs to tell cases apart (`rate_limited`, `login_locked`, `claim_code_locked`, `account_not_approved`, `account_suspended`, `pickup_block`, `report_limit`).

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

### Following and alerts

A buyer can follow a store and gets an alert when it posts food. Buyers also get alerts about their own pickups: a reminder when they reserve, what they saved when the food is handed over, and a plain note if they miss it.

- **Written with the change.** An alert is inserted in the same transaction as the listing or reservation it reports, so there is never one for something that rolled back. "Everyone who follows this store" is a single `INSERT ... SELECT`, however many followers there are.
- **One alert per store per day.** A store posting five listings in an afternoon rewrites one alert ("posted 5 deals") instead of sending five. A partial unique index and `ON CONFLICT ... DO UPDATE` do this in the database.
- **Reminders don't linger.** The pickup reminder is removed when the reservation is picked up, cancelled or missed.
- **Kept for 30 days**, then deleted by the same housekeeping run that expires food.
- The number on the nav follows the live connection: a new alert, or reading one on another device, updates it.

### Photos

A store can put a photo on a listing and on its own page. Photos are stored in Postgres, next to the listings they belong to: one backup, nothing extra to deploy, and a photo can't go missing from its listing. They're small, and browsers are told to keep them for a year, so each one is fetched once per device.

- **Nothing is stored as it was uploaded.** The server decodes the picture and draws it again as a JPEG in two sizes (960 px for pages, 480 px for lists). A file that isn't really a picture fails to decode, and everything else a photo file carries (where it was taken, the phone model) is left behind.
- **Checked from the bytes, not the name.** Only real JPEGs and PNGs get in: no SVG (it can carry scripts), no GIF, no half-uploaded files.
- **Built for a 512 MB server.** The size is read from the file's header before any pixels are, so a tiny file claiming to be 60,000 pixels wide is refused at once. Big photos are decoded at a fraction of their size, and two are processed at a time.
- **Shrunk in the browser first.** The page resizes a phone photo to a few hundred KB before sending it, which matters on mobile data. The server doesn't rely on that.
- **Lists use the small one**, loaded only when scrolled to.
- The same file uploaded twice is stored once. A store keeps up to 200 photos, and ones nothing shows anymore are deleted (unused after a day, or only on listings that ended two months ago).
- "Post again" brings a food's last photo with it.
- **Uploads are off for the shared demo accounts**, so nobody can put a picture on the public demo. To give the demo's sample listings photos, add files to [`backend/src/main/resources/demo/photos`](backend/src/main/resources/demo/photos/README.md).

### Report a problem

A buyer can tell the admins that something went wrong: with one of their pickups (the store was closed, the food wasn't what the listing said, it looked unsafe) or with a store in general. The store isn't told who reported it, and the buyer sees the status and the admin's reply on their account page.

A report is an accusation, so the API keeps it honest: a pickup report must be about the buyer's own reservation from the last 30 days, there's one per pickup and one open report per store, and a buyer can send 5 a day. Deleting an account keeps its reports (the store may still need looking into) but erases what the buyer wrote.

Admins read reports on their Reports screen, longest wait first. Each one shows the pickup it's about and how many reports that store and that buyer have had in 90 days, because a report is one side of the story. Replying closes it: the buyer gets an alert with the reply, and the store is told nothing. When the report is about a pickup the buyer missed (the store was closed, say), the admin can tick "don't count this missed pickup", which is the way out of an unfair missed-pickup block. If two admins answer at the same moment, one wins and the other is told it's already answered (the status check is inside the `UPDATE`).

### Staff accounts

A store's owner can give the people who work the counter their own login, up to five.

- **Staff do the counter work**: list food, hand over pickups, upload photos. **The owner's business stays the owner's**: sales figures, the store profile, staff accounts and closing the store all answer `403` for staff. The check is on the server (`ownedStoreIdFor`), not just a hidden menu.
- **No shared passwords.** The owner picks a username and the server makes a temporary password, shown once and stored only as a hash. Staff are store accounts with the `STAFF` role in `store_members`, which has allowed it since the stores migration.
- **The owner is the way back in.** This app has no email, so "New password" on the staff list replaces a forgotten one and logs that person out everywhere. Removing a staff account does the same and keeps what they listed.
- **A store answers for its staff.** While the owner is suspended, staff can't post or hand over under the store's name. Suspending one staff member locks out only that person.

### Closing a store

An owner can close their store from the account page, with their password. Buyers with a pickup waiting are told not to come (it never counts as a missed pickup), the listings, photos and store page go, followers stop following it, and the owner's and the staff's accounts are deleted. The store's row stays, so past reservations and reports still name it, and its page address isn't handed to someone else. "Download my data" is there to take a copy first.

### Suspending an account

An admin can suspend a buyer or a store from the Users screen, with a reason.

- **It takes effect at once.** Suspending bumps the account's token version, so the login it already holds stops working on its next request, and logging in again is refused with the reason. Only someone with the right password is told the account is suspended.
- **A suspended store owner's store** disappears from the app: its listings come down and its page is gone. Pickups still waiting there are cancelled, and each buyer gets an alert not to go. That never counts as a missed pickup.
- **A suspended buyer's** reservations are cancelled and the food goes back on sale.
- **Lifting it** lets the account log in again. A store's old listings stay down; it posts again when it's ready.
- **Who can't be suspended:** admins, and, by the demo admin, the three accounts every demo visitor logs in with (one visitor shouldn't be able to break the demo for the next).

### Admin activity log

Admins can approve stores, close reports and suspend accounts, so there's a record of who did what: every approval, answer, suspension and lift adds a line with the admin, the time, who it was about and what was said. A line is written in the same transaction as the action, so there's never one without the other, and the API has no way to change or delete a line.

### Live updates

Open screens update on their own over a WebSocket at `/ws`: a stock count drops on every browse page when someone reserves, a new reservation appears on the store's counter screen, and a buyer's ticket flips to "picked up" when the store hands the food over.

- **Sent only after commit.** Services publish a change inside their transaction; it goes out only once that transaction commits (`@TransactionalEventListener`). A checkout that rolls back because something sold out announces nothing.
- **Messages are hints, not data.** Apart from a listing's public stock count, a message only says which part of the screen is out of date (`store`, `reservations`, `alerts`, `deals`, `approvals`), and the screen reloads it through the normal API. Nothing private travels over the socket, and the app still works when the socket is down.
- **Logging in.** A connection starts anonymous and hears public stock changes. The frontend then sends `{"type":"auth","token":"..."}` as its first message; the token is checked exactly like on an API call. It isn't put in the URL, because URLs end up in logs.
- **Staying connected.** The server pings every 25 seconds so proxies keep the connection open. The frontend reconnects with a growing delay, reloads what it may have missed, and lets go of the connection when the tab has been hidden for a minute.
- **One instance.** Connections are kept in memory. With several instances, changes would have to be passed between them (for example with Redis pub/sub).

### Limits

Reserving is free (buyers pay at the counter), so the API limits abuse:

- A buyer holds at most **2 reservations** at a time.
- **2 missed pickups within 30 days** pause reserving for 7 days from the latest miss (all three numbers are settings). Only pickups the buyer simply didn't come for count: not ones cancelled in time, not ones cancelled because the store was suspended, and not ones an admin excused. Checkout answers `403` with code `pickup_block` and when it ends; browsing, following and the cart still work.
- **5 wrong claim codes** on one reservation pause code entry for 15 minutes.
- **5 wrong passwords** from one address pause that username for that address for 15 minutes; 20 from anywhere pause it for everyone. Counting per address means a stranger who knows a username can't lock its owner out. The same limit applies to recovery codes and to the "current password" box.
- Per-address limits on log in, sign-up, checkout and claim-code entry return `429` with `Retry-After`. Every other API call shares a generous per-address limit (1,200 per 10 minutes), so a script can't flood the API.
- Live connections: 500 at once in total, 60 from one address, and 300 opened per address per 10 minutes.
- Photo uploads: 30 per address per 10 minutes. Viewing photos has its own limit (6,000 per 10 minutes), so a page full of pictures doesn't use up the limit for everything else.
- Problem reports: 5 per buyer per day.

### Security notes

- **Passwords** are stored as BCrypt hashes. A failed login takes the same time and gives the same message whether or not the username exists.
- **Tokens** are signed JWTs that last 8 hours. The token only says who is asking: the role, approval and token version are read from the database on every request, so deleting an account, changing its role or changing its password takes effect at once.
- **Recovery codes** are 12 random characters, stored only as hashes. A fast hash is enough because nobody chose them (there's no dictionary to try), and guessing against the live site is stopped by the attempt limits. A wrong code and a username that doesn't exist get the same answer.
- **Every lookup is scoped to the caller.** The user id comes from the verified token, never from the request, and a store can only reach its own listings and pickups.
- **Rate limits use the visitor's own address.** Behind Render that is the `CF-Connecting-IP` header (set in `render.yaml`); `X-Forwarded-For` can be faked there.
- **The demo admin is boxed in.** Anyone can log in as it, so it only sees and approves the demo's sample stores, only sees and answers reports that demo buyers made about demo stores, only sees and suspends the demo's sample accounts (never the shared logins), and only sees demo admins' lines in the activity log.
- **The frontend sends a Content Security Policy** (`frontend/vercel.json`): scripts only from the site itself, API calls and the live connection only to the site and `*.onrender.com`, and no framing by other sites. If the API moves to another domain, update `connect-src` and `img-src` there (photos load from the API).
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
