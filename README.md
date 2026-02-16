# S1000D Viewer (Gradle Multi-Module)

This repository contains a runnable S1000D viewer demo with auth/RBAC, dual HTML render pipelines, applicability filtering phases, and graphics/hotspots.

## Repository layout
- `application/` Spring Boot API
- `webapp/` React + TypeScript SPA
- `data/` runtime sample data (CSDB, published previews, cache)
- `docs/` architecture and run documentation

## Data layout
- `data/csdb/dm/` raw DM XML
- `data/csdb/icn/` graphics assets
- `data/csdb/meta/` DM metadata sidecars
- `data/published/manifest.json` published preview index
- `data/published/dm/<dmId>/index.html` published DM previews
- `data/published/icn/<icnId>.svg` published graphics
- `data/published/hotspots/<icnId>.json` published hotspots
- `data/cache/` render cache (generated, git-ignored)

## Features
- Login with seeded users (`admin`, `eng`, `view`)
- RBAC with upload restricted to `ADMIN`/`ENGINEER`
- Two render modes:
  - `published` (preferred when preview exists)
  - `quick` (XSLT fallback from DM XML)
- Phase 1 applicability filtering (DM-level, aircraft/engine/variant with explainable status)
- Phase 2/3 applicability extension points (skeleton interfaces)
- Three-panel viewer with search highlight and hotspot navigation

## Quick start
- Backend: `./gradlew :application:bootRun`
- Webapp:
  - `cd webapp`
  - `npm install`
  - `npm run dev`
- Tests: `./gradlew test`

Backend: `http://localhost:8080`
Webapp: `http://localhost:5173`

## Runtime configuration
- Java base package: `com.s1000Dorg.viewer`
- App settings are externalized under `viewer.*` in `application/src/main/resources/application.yml`
- Main groups: `viewer.storage`, `viewer.render`, `viewer.applicability`, `viewer.policy`, `viewer.security`
- Environment variables can override secure values (JWT secret and demo passwords).

## Demo credentials (default dev values)
- Admin: `admin / ${VIEWER_DEMO_ADMIN_PASSWORD:-admin123}`
- Engineer: `eng / ${VIEWER_DEMO_ENGINEER_PASSWORD:-eng123}`
- Viewer: `view / ${VIEWER_DEMO_VIEWER_PASSWORD:-view123}`

Wrapper scripts use bundled tooling in `.tools/` first (`.tools/jdk17`, `.tools/gradle`) when present.

See `docs/how-to-run.md` for full Windows steps and:
- `docs/applicability-phase1.md`
- `docs/applicability-phase2.md`
- `docs/applicability-phase3.md`
