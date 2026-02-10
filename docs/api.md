# API Summary

## Authentication
- `POST /api/auth/login`
  - Body: `{ "username": "...", "password": "..." }`
  - Returns JWT token + roles.
- `GET /api/auth/me`
  - Requires bearer token.

## Modules
- `GET /api/modules?aircraft=...&engine=...`
- `GET /api/modules/{dmId}?aircraft=...&engine=...`
- `POST /api/modules/upload` (ADMIN/ENGINEER)
  - multipart form fields: `file`, `title`, `aircraft`, `engine`, `icnId`

## Graphics
- `GET /api/graphics/{icnId}` -> SVG (`image/svg+xml`)
- `GET /api/graphics/{icnId}/hotspots` -> hotspot list

## Admin
- `GET /api/admin/users` (ADMIN only)

## Health
- `GET /api/health`

## Roles
- `ROLE_ADMIN`: all APIs
- `ROLE_ENGINEER`: all except `/api/admin/**`
- `ROLE_VIEWER`: read-only module and graphic APIs