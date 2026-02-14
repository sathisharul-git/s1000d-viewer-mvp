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
Default data root: `data/`

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
- Admin: `admin / admin123`
- Engineer: `eng / eng123`
- Viewer: `view / view123`

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
