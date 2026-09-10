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

Open `http://localhost:5173` in a browser. The homepage fetches `GET /api/postcards` from the backend and renders the results as a grid — see "Browse page" below for what to expect and how to test it with real data.

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

## Image upload (Cloudflare R2)

`POST /api/postcards/{id}/images` uploads front and/or back images for a postcard to Cloudflare R2 and updates `frontImageUrl`/`backImageUrl` with the resulting public URL. It's admin-only, same as the other mutating postcard endpoints.

Configure R2 via env vars (see `backend/.env.example`):

- `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` — used to build the S3 client and authenticate with R2's S3-compatible API.
- `R2_BUCKET_NAME` — the target bucket.
- `R2_PUBLIC_BASE_URL` — the hostname images are actually served from. R2's S3 API endpoint (built from `R2_ACCOUNT_ID`) only accepts signed requests, it's not publicly browsable, so this must be either the bucket's "Public Development URL" (`https://pub-<hash>.r2.dev`, enabled per-bucket in the Cloudflare dashboard) or a custom domain you've attached to the bucket. This is concatenated with the object key to build the URL stored on the postcard.

Without real values, the app still starts (using non-functional placeholders), but any upload attempt will fail when it actually calls R2.

Uploads are validated (JPEG/PNG/WEBP only, 10MB max per file) and go through resize → watermark → compress before upload: always re-encoded as JPEG, resized so width never exceeds 1600px (aspect ratio preserved, keeps R2 storage down per the spec's free-tier budgeting), then watermarked. No unwatermarked copy is ever stored — the watermark is baked into the pixels before the image is uploaded, so it can't be stripped by viewing/saving the file.

The watermark is a semi-transparent, repeating diagonal text pattern (tiled across the whole image, not a single corner) so it can't be defeated by cropping. Text defaults to "Ukrainian Postcards", read from `app.image.watermark-text` in `application.yml` (overridable via the `WATERMARK_TEXT` env var) — change it there rather than hardcoding it anywhere else; it's expected to change.

**1. Create a postcard and log in** (or reuse an existing postcard's id):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin1@example.com","password":"ChangeMe123!"}' | jq -r .token)

ID=$(curl -s -X POST http://localhost:8080/api/postcards \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Test","author":"Unknown","description":"...","color":"COLOR","location":"Kyiv","frontImageUrl":"https://example.com/placeholder.jpg","backImageUrl":"https://example.com/placeholder.jpg"}' \
  | jq -r .id)
```

**2. Upload images** — `front` and `back` are both optional, but at least one is required; each is a normal multipart file part:

```bash
curl -X POST "http://localhost:8080/api/postcards/$ID/images" \
  -H "Authorization: Bearer $TOKEN" \
  -F "front=@/path/to/front.jpg;type=image/jpeg" \
  -F "back=@/path/to/back.jpg;type=image/jpeg"
# 200 OK with the updated postcard, frontImageUrl/backImageUrl now point at R2
```

Omitting the token returns `401`. An unsupported content type, an oversized file, or a request with neither `front` nor `back` returns `400` with a JSON body like `{"message":"..."}`.

**3. Verify the image actually landed in the bucket** — the response's `frontImageUrl`/`backImageUrl` should load directly in a browser or via curl (this fetches straight from `R2_PUBLIC_BASE_URL`, not through the backend):

```bash
curl -I "$(curl -s http://localhost:8080/api/postcards/$ID | jq -r .frontImageUrl)"
# HTTP/1.1 200, content-type: image/jpeg
```

You can also check the Cloudflare dashboard (R2 → your bucket) and look for an object under `postcards/<id>/`, or list it with any S3-compatible client pointed at `https://<R2_ACCOUNT_ID>.r2.cloudflarestorage.com` using your `R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`.

**4. Visually confirm the watermark** — open the `frontImageUrl`/`backImageUrl` from the response directly in a browser (or reuse the same URL from step 3):

```bash
curl -s http://localhost:8080/api/postcards/$ID | jq -r .frontImageUrl
# paste the printed URL into a browser
```

You should see "Ukrainian Postcards" repeated diagonally across the image, semi-transparent and legible over both light and dark areas of the photo, without hiding the postcard itself.

**5. Deleting a postcard also deletes its images from R2** — `DELETE /api/postcards/{id}` removes the front/back objects from the bucket before removing the DB row (a postcard whose images were never uploaded — still pointing at some other URL — deletes cleanly too, nothing to remove). Confirm the image is actually gone with a `HEAD` request before and after:

```bash
FRONT_URL=$(curl -s http://localhost:8080/api/postcards/$ID | jq -r .frontImageUrl)
curl -I "$FRONT_URL"
# HTTP/1.1 200 before deleting

curl -X DELETE "http://localhost:8080/api/postcards/$ID" -H "Authorization: Bearer $TOKEN"
# 204

curl -I "$FRONT_URL"
# HTTP/1.1 404 — the object is gone from R2, not just the DB row
```

Same thing is visible in the Cloudflare dashboard (R2 → your bucket → `postcards/<id>/`) — the folder should be empty/gone immediately after the delete call.

Re-uploading an image on a postcard that already has one works the same way: the old R2 object is deleted before the new one is stored, so replacing a photo never leaves the previous file behind.

## Browse page

The homepage (`http://localhost:5173/`) fetches `GET /api/postcards` and renders a responsive grid of cards (front image thumbnail, title, year). No new env vars or config — it uses the existing `VITE_API_URL` the frontend already reads for everything else. React Router was added (`react-router-dom`) to support this and the new `/postcards/:id` route.

**To see it with real data**, create a few postcards first (no auth needed to view, but creating them is admin-only — see "Admin authentication" above for getting a token):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin1@example.com","password":"ChangeMe123!"}' | jq -r .token)

for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/api/postcards \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Sample Postcard $i\",\"year\":$((1900+i)),\"author\":\"Unknown\",\"description\":\"...\",\"color\":\"COLOR\",\"location\":\"City $i\",\"frontImageUrl\":\"https://placehold.co/400x300?text=Front+$i\",\"backImageUrl\":\"https://placehold.co/400x300?text=Back+$i\"}" > /dev/null
done
```

Then open `http://localhost:5173/` and check:

- **Grid** — a card per postcard showing the front image, title, and year, reflowing responsively (try resizing the window/narrowing to mobile width).
- **Empty state** — with zero postcards (delete them all via `DELETE /api/postcards/{id}`), the page should show "No postcards yet. Check back soon." rather than a blank screen.
- **Loading state** — briefly visible on first load/page change ("Loading postcards…"); easiest to see on a throttled network (DevTools → Network → Slow 3G) or just watch closely on refresh.
- **Pagination** — 12 postcards per page. Create more than 12 to get a second page; "Previous"/"Next" should enable/disable correctly at the first/last page, and the "Page X of Y" label should update.
- **Card navigation** — clicking a card should go to `/postcards/<id>` and show "Detail page coming soon" with that postcard's ID. The URL bar should update (it's a real route, not a modal), and browser back should return to the grid.

## Search

`GET /api/postcards/search?q=...&color=...&year=...&location=...` — all params optional, same paginated response shape as `GET /api/postcards`. `q` matches (case-insensitive, partial) across `title`, `author`, `location`, and `description`; `color`/`year`/`location` are independent exact/partial filters that combine with `q` via AND when several are given. No auth required — it's public like the rest of the read endpoints. No new env vars.

**Backend — curl examples** (using the same sample postcards from "Browse page" above, or create your own):

```bash
# Partial, case-insensitive match — matches "Paris, France" in location
curl -s "http://localhost:8080/api/postcards/search?q=paris" | jq

# Matches free text against description too
curl -s "http://localhost:8080/api/postcards/search?q=eiffel" | jq

# Filter by color only (no text query)
curl -s "http://localhost:8080/api/postcards/search?color=BLACK_AND_WHITE" | jq

# Filter by year only
curl -s "http://localhost:8080/api/postcards/search?year=1950" | jq

# Combine a text query with a filter
curl -s "http://localhost:8080/api/postcards/search?q=ukraine&color=COLOR" | jq

# No params at all - same as GET /api/postcards
curl -s "http://localhost:8080/api/postcards/search" | jq

# No matches - 200 with empty content, not an error
curl -s "http://localhost:8080/api/postcards/search?q=nonexistentxyz" | jq
```

**Frontend** — the search box lives above the grid on the homepage. To confirm debouncing and the empty-results state in the browser:

- Type a query and watch the Network tab (or just watch the grid) — the grid shouldn't update, and no request should fire, until ~300ms after you stop typing. Typing "paris" at a normal pace should produce exactly one request to `/api/postcards/search`, not five.
- While results are showing, the grid replaces the normal browse view entirely (pagination still works, now paginating the search results).
- Type something that matches nothing (e.g. `zzzznomatch`) — you should see "No postcards match your search." (distinct from the "No postcards yet" message shown when the whole collection is empty).
- Clear the search box (or click the input's native × ) — the grid should return to the normal paginated browse view, starting back at page 1.

## Seeding sample data

`scripts/seed-dummy-data.sh` logs in as an admin, creates 12 made-up postcard records, and uploads a small set of placeholder images (downloaded once from picsum.photos into `~/Desktop/postcard-seed-images`) as each one's front/back images — a quick way to populate the browse grid/search with more than a couple of rows instead of creating postcards one at a time via curl.

Requires `SEED_ADMIN_PASSWORD` (password for one of the seeded admin accounts) as an env var; `SEED_ADMIN_EMAIL` is optional and defaults to `admin1@example.com`.

```bash
SEED_ADMIN_PASSWORD='ChangeMe123!' bash scripts/seed-dummy-data.sh
```

Note: the script's `curl -o` image download doesn't follow picsum.photos' redirect (no `-L`), so as written it saves empty files and every image upload silently fails validation — the script doesn't check the upload response, so it reports success regardless. The 12 postcard records still get created, just without real images, until that's fixed.

## Project structure

```
/backend   Spring Boot 4.1, Java 25, Maven — REST API + Postgres via Spring Data JPA
/frontend  React + Vite + TypeScript
```

Postcard CRUD API secured behind JWT-based admin auth, Cloudflare R2 image upload with resizing/watermarking, dynamic search, and a browse grid on the frontend — see the sections above for details on each.
