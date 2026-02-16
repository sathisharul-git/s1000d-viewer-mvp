# Run Guide (Windows + VS Code)

## Prerequisites
- JDK 17+
- Node.js 20+
- Docker Desktop (for local Oracle + LDAP + OPA)

## 0) Start local enterprise stack
From repository root:

```powershell
docker compose up -d
```

Wait for:
- Oracle logs contain `DATABASE IS READY TO USE!`
- OPA responds at `http://localhost:8181/health`

## 1) Start backend (application)
From repository root:

```powershell
./gradlew :application:bootRun
```

Backend URL: `http://localhost:8080`
Default roots are configured via `s1000d.storage.*` in `application/src/main/resources/application.yml`.
Default JDBC URL: `jdbc:oracle:thin:@localhost:1521/XEPDB1`.
Default LDAP URL: `ldap://localhost:389`.
Default OPA URL: `http://localhost:8181`.

## 2) Start webapp
In a second terminal:

```powershell
cd webapp
npm install
npm run dev
```

Webapp URL: `http://localhost:5173`

Vite proxies `/api` to `http://localhost:8080`.

## 3) Authentication
Default mode is LDAP-backed authentication:
- Admin: `admin / admin123`
- Engineer: `eng / eng123`
- Viewer: `view / view123`

Optional fallback for local debug only:
- Enable profile: `SPRING_PROFILES_ACTIVE=dev-auth`
- Uses `application-dev-auth.yml` demo users.

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
$env:S1000D_DB_URL = "jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:S1000D_LDAP_URL = "ldap://localhost:389"
$env:S1000D_OPA_URL = "http://localhost:8181"
$env:VIEWER_SECURITY_JWT_SECRET = "replace-this-for-enterprise"
./gradlew :application:bootRun
```

Key groups:
- `S1000D_DB_*` for Oracle datasource
- `S1000D_LDAP_*` for LDAP auth
- `S1000D_OPA_*` for policy decisions
- `S1000D_*_ROOT` for vault paths
- `VIEWER_SECURITY_*` for JWT/OIDC/claims
