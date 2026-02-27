# API Summary

## Authentication
- `POST /api/auth/login`
  - Body: `{ "username": "...", "password": "..." }`
  - Returns JWT token + roles.
- `GET /api/auth/me`
  - Requires bearer token.

## Modules
- `GET /api/modules?aircraft=...&engine=...&variant=...`
  - Optional dynamic context keys: `pa.<key>=<value>` (example: `pa.type=...&pa.serialNo=...`)
  - Returns:
    - `filters`
    - `modules[]` (`dmId`, `title`, `applicability`, `source`, `hasPublishedPreview`, `applicabilityResult`, `applicabilityReason`, `applicabilitySource`)
- `GET /api/modules/{dmId}/render?aircraft=...&engine=...&variant=...`
  - Optional: `pmcId=...` (enforces publication scope for render)
  - Optional dynamic context keys: `pa.<key>=<value>`
  - Returns:
    - `dmId`, `source` (`published|quick`), `html`
    - `applicability` (`dmStatus`, `displayText`, `reason`, `source`)
    - `inlineApplicability` (`mode`, `removedCount`, `keptCount`)
    - `meta` (`title`, `applicability`, `applicabilityResult`, `applicabilityReason`, `applicabilitySource`)
    - `assets.icns[]`
    - `links.dmRefs[]`
- `POST /api/modules/upload` (ADMIN/ENGINEER)
  - multipart fields: `file`, `title`, `aircraft`, `engine`, `variant`, `icnId`

## Publications (PMC scope)
- `GET /api/pmc`
  - Returns available PMCs (`pmcId`, `title`).
- `GET /api/publications/{pmcId}/modules?aircraft=...&engine=...&variant=...`
  - Optional dynamic context keys: `pa.<key>=<value>`.
  - Returns modules in PMC order, already applicability-filtered (`NOT_APPLICABLE` hidden by default).
  - Each row includes:
    - `dmId`, `displayName`, `systemCode`, `infoCode`
    - `dmApplicabilityStatus`, `dmApplicabilityDisplayText`, `dmApplicabilityReason`, `dmApplicabilitySource`
    - `applicability`, `source`, `hasPublishedPreview`

## Graphics
- `GET /api/graphics/{icnId}` -> SVG (`image/svg+xml`)
- `GET /api/graphics/{icnId}/hotspots` -> hotspot list

## Admin
- `GET /api/admin/users` (ADMIN only)

## Health
- `GET /api/health`

## Roles
- `ROLE_ADMIN`: full access
- `ROLE_ENGINEER`: all except `/api/admin/**`
- `ROLE_VIEWER`: read-only module/graphics
