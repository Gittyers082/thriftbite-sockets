# ThriftBite (Java Sockets)

A client-server marketplace for surplus food. Store owners list near-expiry food at a discount, and consumers browse, add to cart and check out. Built with raw Java TCP sockets, multithreading and Swing.

> 🚧 Being upgraded to Spring Boot, WebSockets, PostgreSQL and React + TypeScript. See also **thriftbite-rmi**, the same system rebuilt with Java RMI.

Originally a team university project (Team 7).

> ⚠️ **Educational code. Run it locally only.** This version decodes client messages with `java.beans.XMLDecoder`, does not check user roles on the server, and stores passwords in plain text. Don't expose the server on a public network. These issues are fixed in the upgraded version.

## My role

**Upgrade (in progress):** I'm rebuilding the whole project myself, both backend and frontend, with AI-assisted development.

**Original version (Team 7):** I was the team's **backend developer** and built the server side:

- **`server/ServerMain.java`**: the TCP server that accepts connections and starts one handler thread per client
- **`server/ClientHandler.java`**: request handling for all 25 protocol actions (login, registration, items, cart, checkout, handover, admin actions)
- **`server/XMLRepository.java`**: the persistence layer for users, items, carts and transactions

Teammates built the Swing client (views, controllers and CRUD panels).

<!-- Screenshots: add owner, consumer and admin dashboard images to docs/ and link them here -->

## Features

- **Three roles:** Owner (list and manage food items), Consumer (browse, cart, checkout), Admin (approve users, manage items)
- **Multithreaded TCP server:** one handler thread per connected client
- **Expiry tracking:** items expiring soon are flagged, with days/hours left shown to owners
- **MVC Swing client:** models, views and controllers kept in separate packages
- **XML persistence:** users, items, carts and transactions are saved to XML files

## Tech

Java 21 · `java.net` sockets · Swing · `java.beans` XMLEncoder/XMLDecoder · Maven

## Run it in VS Code

1. Install **JDK 21+** and the **Extension Pack for Java** (VS Code will prompt you).
2. Open this folder in VS Code and wait for the Java project to load.
3. Open **Run and Debug** and pick **Server + 2 Clients**, then press ▶.
   This opens the server window and two client windows.
4. In the server window, click **▶ START SERVER** (it listens on port `16767`), then log in from each client.

You can also start **Server**, **Client 1** or **Client 2** on their own.

### From the terminal

```bash
mvn compile
mvn exec:java                                                       # server
mvn exec:java -Dexec.mainClass=com.thriftbite.MVC.view.client.client1  # client
```

Run these from the project root: the server reads and writes the `*.xml` data files in the current directory.

## Demo accounts

All demo accounts use the password `demo1234`.

| Username   | Role     |
|------------|----------|
| `godmode`  | Admin    |
| `johnpork` | Owner    |
| `legends`  | Owner    |
| `zilong`   | Consumer |
| `speed`    | Consumer |

## Project structure

```
src/com/thriftbite/
├── server/      # ServerMain (accepts connections), ClientHandler (per-client thread), XMLRepository
├── common/      # Request/Response protocol, DTOs, ClientSocketManager
├── MVC/         # Swing client: model/, view/, controller/
└── CRUDS/       # Item create/read/update/delete panels
```

## Known limitations

These are left as-is in this version and are being addressed in the upgrade:

- Passwords are stored and compared in plain text
- XML files are the database (no concurrency control)
- No automated tests
