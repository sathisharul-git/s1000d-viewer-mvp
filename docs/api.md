# API Summary

## Authentication
- `POST /api/auth/login`
  - Body: `{ "username": "...", "password": "..." }`
  - Returns JWT token + roles.
- `GET /api/auth/me`
  - Requires bearer token.

## Modules
- `GET /api/modules?aircraft=...&engine=...`
  - Returns:
    - `filters`
    - `modules[]` (`dmId`, `title`, `applicability`, `source`, `hasPublishedPreview`)
- `GET /api/modules/{dmId}/render?aircraft=...&engine=...`
  - Returns:
    - `dmId`, `source` (`published|quick`), `html`
    - `meta` (`title`, `applicability`, `applicabilityResult`)
    - `assets.icns[]`
    - `links.dmRefs[]`
- `POST /api/modules/upload` (ADMIN/ENGINEER)
  - multipart fields: `file`, `title`, `aircraft`, `engine`, `icnId`

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
