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

## Admin authentication

`GET` endpoints under `/api/postcards` stay public. Creating, updating, or deleting a postcard requires a JWT obtained by logging in as one of the seeded admin accounts.

The signing secret is read from the `JWT_SECRET` env var (see `backend/.env.example`); if unset, an insecure development-only default is used (see `application.yml`) — always set a real value before any real deployment. Tokens expire after 24 hours.

**1. Log in to get a token** (using the local-dev placeholder credentials — substitute your own if you set `ADMIN1_EMAIL`/`ADMIN1_PASSWORD`):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin1@example.com","password":"ChangeMe123!"}'
# {"token":"eyJhbGciOiJIUzI1NiJ9...."}
```

A wrong password or unknown email returns `401 Unauthorized`.

**2. Use the token to call a protected endpoint** — save it to a variable and pass it as a Bearer token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin1@example.com","password":"ChangeMe123!"}' | jq -r .token)

curl -X POST http://localhost:8080/api/postcards \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Greetings from Odesa",
    "year": 1928,
    "author": "Unknown",
    "description": "A view of the opera house.",
    "color": "BLACK_AND_WHITE",
    "location": "Odesa, Ukraine",
    "frontImageUrl": "https://example.com/odesa-front.jpg",
    "backImageUrl": "https://example.com/odesa-back.jpg"
  }'
# 201 Created with the new postcard
```

Omitting the `Authorization` header, or sending a malformed/expired/invalid token, returns `401 Unauthorized` without reaching the controller. `PUT /api/postcards/{id}` and `DELETE /api/postcards/{id}` work the same way — same header, no token required for `GET`.

## Project structure

```
/backend   Spring Boot 4.1, Java 25, Maven — REST API + Postgres via Spring Data JPA
/frontend  React + Vite + TypeScript
```

No business logic yet — this is setup only, confirming both halves run and talk to each other.
