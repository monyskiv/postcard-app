# Postcard Collection Web App — Spec (v1)

## Overview
A web app for browsing and managing a personal postcard collection (500–5,000 items). Public visitors can browse and search postcards; two admins can add, edit, and delete records.

## Tech Stack
- **Frontend:** React
- **Backend:** Java 25 (LTS recommended; Java 26 also works) / Spring Boot 4.1 (REST API)
- **Database:** PostgreSQL
- **Image storage:** Cloudflare R2 (S3-compatible API)
- **Hosting:** Render (backend + Postgres) or split — Render for backend, Neon for Postgres; Vercel/Netlify for frontend

## Data Model

### Postcard
| Field | Type | Notes |
|---|---|---|
| id | UUID / long | primary key |
| title | string | required |
| year | int (nullable) | postcards are often undated |
| author | string | sender, publisher, or "unknown" |
| description | text | free-form |
| color | enum: `COLOR`, `BLACK_AND_WHITE` | required |
| location | string | place **depicted** on the postcard — plain text v1, structured later if needed |
| front_image_url | string | required |
| back_image_url | string | required |
| created_at / updated_at | timestamp | auto |

**Note:** `location` is scoped to depicted location only (not mailing origin/destination). If postmark/mailing info becomes relevant later, add it as a separate field (e.g. `origin_location`) rather than overloading this one.

### Admin (User)
| Field | Type | Notes |
|---|---|---|
| id | UUID | |
| email | string | unique |
| password_hash | string | bcrypt |
| created_at | timestamp | |

Two admin accounts (you + your friend), no public self-registration.

## API Endpoints

**Public**
- `GET /api/postcards` — list/browse, paginated, supports query params for search/filter
- `GET /api/postcards/{id}` — single postcard detail
- `GET /api/postcards/search?q=...&color=...&year=...&location=...` — dynamic search

**Admin (authenticated)**
- `POST /api/auth/login` — returns JWT
- `POST /api/postcards` — create
- `PUT /api/postcards/{id}` — update
- `DELETE /api/postcards/{id}` — delete record; also deletes the associated front/back images from R2 so no orphaned objects remain in the bucket
- `POST /api/postcards/{id}/images` — upload front/back images (proxies to R2 or issues a pre-signed upload URL). Re-uploading (replacing an existing image on a postcard that already has one) deletes the old R2 object before storing the new one, for the same reason.

## Pages

1. **Home** — intro + entry point to search/browse (mirrors the CatalogIt reference: could show category/color counts as a landing overview)
2. **Search / Browse** — search box (dynamic — debounced, hits `/search` as you type), filter by color/year/location, grid of results (thumbnail = front image, title, year)
3. **Postcard Detail** — full front + back image, all metadata; "related postcards" is v2 (noted below)
4. **Admin Login** — email/password, separate accounts for you and your friend
5. **Admin Dashboard** — table of postcards with add/edit/delete; edit form handles metadata + front/back image upload

## Search (v1 scope)
Dynamic search box filtering on `title`, `author`, `location`, `description` (simple `ILIKE`/full-text query in Postgres — no need for Elasticsearch at this scale). Debounce input ~300ms before hitting the API.

## Auth
Two admin accounts, JWT-based session, simple email+password login. No public accounts needed for v1 (browsing is open to everyone).

## Out of scope for v1 (explicitly deferred)
- "Related postcards" / recommendations on the detail page
- Structured location (lat/long, mapping)
- Public user accounts, comments, favorites

## Hosting Plan
| Layer | Service | Free tier notes |
|---|---|---|
| Frontend | Vercel/Netlify | free, no real caveats |
| Backend | Render | free web service, cold starts after idle (~30–60s wake) |
| Database | Render Postgres or Neon | free up to ~0.5–1GB, plenty for this dataset's text |
| Images | Cloudflare R2 | free up to 10GB storage, no egress fees |

At 500–5,000 postcards with 2 images each, budget for image storage: even at ~2MB/image average, 5,000 postcards × 2 images × 2MB ≈ 20GB — likely to exceed R2's 10GB free tier eventually. Worth compressing/resizing images on upload (e.g. cap at 1600px wide) to stay under the limit longer.

## Watermarking
Images are watermarked server-side, baked into the pixels, before being stored in R2 — not a client-side/CSS overlay, since those don't survive a right-click save and would defeat the purpose. No clean/original copy is kept in the cloud (originals already live on the external hard drive used for uploading, so a duplicate unwatermarked cloud copy is unnecessary storage cost). Only the watermarked version is ever stored and served.

Pipeline at upload time: resize (cap ~1600px wide) → watermark → store in R2. Watermark text starts as "Ukrainian Postcards" (placeholder, configurable — expect this to change).

## Suggested Build Order (small, reviewable chunks — good fit for stacked PRs)
1. Postcard entity + repository + migration
2. CRUD REST endpoints (no auth yet)
3. Admin auth (JWT, two seeded accounts)
4. Lock down admin endpoints behind auth
5. Image upload → R2 integration
6. Server-side watermarking, baked into the upload pipeline (resize → watermark → store)
7. Frontend: home + browse/grid page
8. Frontend: search box (dynamic)
9. Frontend: postcard detail page
10. Frontend: admin dashboard (CRUD UI) — metadata creation and image upload are kept as two distinct steps in the UI, matching the two separate backend calls (POST /api/postcards, then POST /api/postcards/{id}/images), rather than one combined form
11. Deploy each layer, wire up env vars/URLs between them

Each numbered item above is a reasonable single PR (or stacked PR) — small enough to review, and each one leaves the app in a working state.