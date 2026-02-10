# Architecture Overview

## Modules
- `backend`: Spring Boot API (auth, RBAC, module rendering, graphics/hotspots, upload)
- `frontend`: React + TypeScript SPA (login + three-panel viewer)
- `sample-data`: file-based CSDB demo content

## Backend flow
1. `POST /api/auth/login` returns JWT.
2. Frontend sends `Authorization: Bearer <token>` on all `/api/**` requests.
3. Spring Security enforces RBAC:
   - `ROLE_ADMIN`: all endpoints, including `/api/admin/users`
   - `ROLE_ENGINEER`: upload + read endpoints
   - `ROLE_VIEWER`: read-only endpoints

## Data layout
Default runtime source: `sample-data/csdb/S1000D_4-1_Bike_Samples` (flat CSDB package).

Supported layouts:
- Flat CSDB package (bike sample): `DMC-*.XML`, `ICN-*.(CGM|PNG|JPG|GIF)` in one folder
- Structured layout (legacy demo): `dm/`, `icn/`, and `hotspots/` subfolders

Metadata and hotspot behavior:
- Applicability/title/ICN are parsed from DM XML when sidecar metadata is missing.
- If `hotspots/<icnId>.json` exists, it is used directly.
- Otherwise, hotspots are derived from `<graphic><hotspot .../></graphic>` in matching DM XML.

## Rendering and search
- Backend converts DM XML into a simple HTML article for preview.
- Frontend applies client-side text highlight (`<mark>`) to the rendered HTML.

## CGM extension point
- Interface: `CgmToSvgConverter`
- Primary implementation: `JcgmBackedCgmToSvgConverter`
- Fallback implementation: `DemoCgmToSvgConverter`
- Conversion behavior:
  - If `jcgm` jars are on classpath or in `backend/libs/jcgm`, backend uses `jcgm` ImageIO SPI to decode CGM and wraps rendered image in SVG.
  - If `jcgm` is unavailable or decode fails, backend falls back to deterministic SVG generation from CGM payload bytes.

## Validation
- Upload validation includes XML well-formed check.
- XSD validation hook exists in `XmlValidationService#validateAgainstXsdHook`.
