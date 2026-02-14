# Run Guide (Windows + VS Code)

## Prerequisites
- JDK 17+
- Gradle 8+
- Node.js 20+

## 1) Start backend
From repository root:

```powershell
./gradlew :application:bootRun
```

Backend URL: `http://localhost:8080`
Default data root: `sample-data/csdb/S1000D_4-1_Bike_Samples`

## 2) Start frontend
In a second terminal:

```powershell
./gradlew :webapp:npmRunDev
```

Frontend URL: `http://localhost:5173`

Vite is preconfigured to proxy `/api` to `http://localhost:8080`.

## 3) Demo credentials
- Admin: `admin / admin123`
- Engineer: `eng / eng123`
- Viewer: `view / view123`

## 4) Run tests
From repository root:

```powershell
./gradlew test
```

## Optional: enable standards-based CGM decoding (`jcgm`)
The backend can use `jcgm` if jars are available at runtime.

1. Create directory: `application/libs/jcgm`
2. Copy `jcgm` jars (for example `jcgm-core*.jar`, `jcgm-image*.jar`) into that folder.
3. Restart backend (`./gradlew :application:bootRun`).

If jars are missing, backend automatically falls back to the built-in demo converter.

## 5) VS Code debug
- Use launch profile: `Backend: Spring Boot`
- File: `.vscode/launch.json`

## Typical demo flow
1. Login as `view` and browse/filter modules.
2. Pick a module with graphics (for example `DMC-S1000DBIKE-AAA-D00-00-00-00AA-041A-A_009-00_EN-US`).
3. Use center-panel search to highlight terms.
4. Click hotspot in right panel to navigate linked module.
5. Login as `eng` and upload a DM XML using left panel upload form.
6. Login as `admin` and open user list.
