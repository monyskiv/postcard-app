# Postcard App

Full-stack scaffold: Spring Boot backend (`/backend`) + React/Vite frontend (`/frontend`), backed by Postgres in Docker.

## Prerequisites

- Java 25
- Maven (or use the included `./mvnw` wrapper)
- Node.js 20+
- Docker (with Compose)

## 1. Start the database

```bash
docker compose up -d
```

Starts a local Postgres 16 instance on `localhost:5432` (db `postcards`, user/password `postcards`).

## 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`. Connection settings are read from `DB_URL`, `DB_USER`, `DB_PASSWORD` env vars, defaulting to the local Docker Postgres above (see `src/main/resources/application.yml`).

Two admin accounts are seeded by a Flyway migration on first startup, using credentials read from `ADMIN1_EMAIL`/`ADMIN1_PASSWORD` and `ADMIN2_EMAIL`/`ADMIN2_PASSWORD` env vars (see `backend/.env.example`). **If these env vars are not set, insecure placeholder credentials are used instead** (`admin1@example.com` / `ChangeMe123!` and `admin2@example.com` / `ChangeMe456!`) — fine for local dev, but these placeholders must never be used in a real deployment. Since the migration only runs once, changing these env vars later has no effect on already-seeded rows — update the `admins` table directly (or add a new migration) to rotate credentials post-deployment.

Verify:

```bash
curl http://localhost:8080/api/health
# {"status":"ok"}
```

## 3. Start the frontend

```bash
cd frontend
cp .env.example .env   # already provided for local dev, edit if needed
npm install
npm run dev
```

Runs on `http://localhost:5173`.

## 4. Verify end to end

Open `http://localhost:5173` in a browser. The homepage fetches `/api/health` from the backend and displays the result — you should see `status: ok`.

## Project structure

```
/backend   Spring Boot 4.1, Java 25, Maven — REST API + Postgres via Spring Data JPA
/frontend  React + Vite + TypeScript
```

No business logic yet — this is setup only, confirming both halves run and talk to each other.
