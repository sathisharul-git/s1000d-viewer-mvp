# Run Guide (Windows + VS Code)

## Prerequisites
- JDK 17+
- Node.js 20+

## 1) Start backend (application)
From repository root:

```powershell
./gradlew :application:bootRun
```

Backend URL: `http://localhost:8080`
Default roots are configured via `viewer.storage.*` in `application/src/main/resources/application.yml`.

## 2) Start webapp
In a second terminal:

```powershell
cd webapp
npm install
npm run dev
```

Webapp URL: `http://localhost:5173`

Vite proxies `/api` to `http://localhost:8080`.

## 3) Demo credentials
- Admin: `admin / ${VIEWER_DEMO_ADMIN_PASSWORD:-admin123}`
- Engineer: `eng / ${VIEWER_DEMO_ENGINEER_PASSWORD:-eng123}`
- Viewer: `view / ${VIEWER_DEMO_VIEWER_PASSWORD:-view123}`

## 4) Run tests
From repository root:

```powershell
./gradlew test
```

## Optional: enable standards CGM decoding (`jcgm`)
1. Create directory: `application/libs/jcgm`
2. Copy `jcgm` jars (for example `jcgm-core*.jar`, `jcgm-image*.jar`) into that folder.
3. Restart backend (`./gradlew :application:bootRun`).

If jars are missing, backend returns demo fallback SVG for CGM-only graphics.

## 5) VS Code debug
- Launch profile: `Backend: Spring Boot`
- File: `.vscode/launch.json`
- Optional tasks: `.vscode/tasks.json`

## 6) Environment overrides
Examples:

```powershell
$env:VIEWER_SECURITY_JWT_SECRET = "replace-this-for-enterprise"
$env:VIEWER_DEMO_ADMIN_PASSWORD = "replace-admin-password"
$env:VIEWER_CORS_ORIGIN_1 = "http://localhost:5173"
./gradlew :application:bootRun
```

Key groups:
- `VIEWER_SECURITY_*` for JWT/OIDC/claims
- `VIEWER_DEMO_*` for demo user passwords
- `VIEWER_CORS_*` for allowed origins
