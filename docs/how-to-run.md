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

## 7) One-command startup after deployment
Use a wrapper script so operations can start the full app stack with one command.

### 7.1 Build artifacts
From repository root:

```powershell
./gradlew :application:bootJar
cd webapp
npm ci
npm run build
cd ..
```

### 7.2 Windows script (`start-prod.ps1`)
```powershell
docker compose up -d
New-Item -ItemType Directory -Force logs | Out-Null

Start-Process java -ArgumentList '-jar','application/build/libs/application-0.1.0.jar' `
  -RedirectStandardOutput 'logs/backend.log' -RedirectStandardError 'logs/backend.err.log'
Start-Process npx -ArgumentList 'serve','-s','webapp/dist','-l','5173' `
  -RedirectStandardOutput 'logs/webapp.log' -RedirectStandardError 'logs/webapp.err.log'
```

Run:

```powershell
.\start-prod.ps1
```

### 7.3 Linux script (`start-prod.sh`)
```bash
#!/usr/bin/env bash
set -e

docker compose up -d
mkdir -p logs

nohup java -jar application/build/libs/application-*.jar > logs/backend.log 2>&1 &
nohup npx serve -s webapp/dist -l 5173 > logs/webapp.log 2>&1 &
```

Run:

```bash
./start-prod.sh
```

## 8) Containerized deploy package
Use the deployment assets in `deploy/` to run on any host with Docker.

### 8.1 Prepare env
From repo root:

```powershell
Copy-Item deploy/.env.example deploy/.env
```

Edit `deploy/.env` as needed (DB URL/user/pass, JWT secret, ports).

### 8.2 Start full stack
From repo root:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.deploy.yml up -d --build
```

Services:
- Webapp: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Oracle: `localhost:1521`
- LDAP: `localhost:389`
- OPA: `localhost:8181`

### 8.3 Stop stack

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.deploy.yml down
```

## 9) Build a portable deploy bundle
Linux/macOS:

```bash
./deploy/package-deploy.sh
```

Windows:

```powershell
.\deploy\package-deploy.ps1
```

Generated artifacts:
- `deploy/package/s1000d-viewer/`
- `deploy/package/s1000d-viewer-deploy.tar.gz` or `deploy/package/s1000d-viewer-deploy.zip`
