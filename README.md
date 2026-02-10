# S1000D Viewer (Gradle Multi-Module)

This repository contains a runnable demo S1000D viewer with authentication, role-based access control, applicability filtering, text search highlight, and hotspot-aware graphics navigation.

## Repository layout
- `backend/` Spring Boot API
- `frontend/` React + TypeScript SPA
- `sample-data/` DM XML, graphics, metadata sidecars, hotspots
- `docs/` architecture and run documentation

## Features
- Login with seeded users (`admin`, `eng`, `view`)
- RBAC:
  - `ADMIN`: full access + user listing + upload
  - `ENGINEER`: upload + view/search/filter
  - `VIEWER`: view/search/filter only
- S1000D module browsing and simple HTML rendering
- Applicability filters (`aircraft`, `engine`)
- Graphics endpoint serving SVG with dual CGM conversion path (optional `jcgm` jars in `backend/libs/jcgm` for standards decode; built-in fallback converter otherwise)
- Hotspot endpoint and hotspot-driven module navigation
- Upload endpoint for DM XML with well-formed XML validation
- Default sample source: `sample-data/csdb/S1000D_4-1_Bike_Samples`

## Quick start
- Backend: `./gradlew :backend:bootRun`
- Frontend: `./gradlew :frontend:npmRunDev`
- Test: `./gradlew test`
- Wrapper scripts use bundled tooling in `.tools/` first (`.tools/jdk17`, `.tools/gradle`) when present.

See `docs/how-to-run.md` for full Windows steps.
API details are in `docs/api.md`.
