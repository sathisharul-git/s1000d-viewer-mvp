# S1000D Viewer (Gradle Multi-Module)

This repository contains an S1000D viewer with Oracle-backed CSDB metadata, vault-based file storage, LDAP authentication, and OPA authorization.

## Repository layout
- `application/` Spring Boot backend
- `webapp/` React + TypeScript frontend
- `data/` runtime CSDB/published/cache files
- `docs/` architecture and run guides

## Data layout
- `data/csdb/dm/` raw DM XML
- `data/csdb/icn/` graphics assets
- `data/csdb/meta/` metadata sidecars
- `data/published/manifest.json` published preview manifest
- `data/published/dm/<dmId>/index.html` published DM HTML
- `data/published/icn/<icnId>.svg` published graphics
- `data/published/hotspots/<icnId>.json` hotspot metadata
- `data/cache/` generated cache files (git-ignored)

## Storage architecture
- Database (Oracle/H2) stores metadata and relationships only:
  - DM/PMC/ICN metadata
  - vault relative paths
  - file hashes and timestamps
  - PMC->DM and DM->ICN mappings
  - applicability metadata
- Physical files stay in the vault filesystem.
- XML/CGM/HTML file content is never stored in the database.

## Features
- OpenLDAP authentication by default
- OPA policy-based authorization checks
- `dev-auth` fallback profile for local/test only
- Published vs quick DM rendering
- DM-level applicability filtering (aircraft/engine/variant)
- Graphics rendering with hotspot support

## Quick start
1. Start local enterprise stack: `docker compose up -d`
2. Start backend: `./gradlew :application:bootRun`
3. Start webapp:
   - `cd webapp`
   - `npm install`
   - `npm run dev`
4. Run tests: `./gradlew test`

Backend: `http://localhost:8080`  
Webapp: `http://localhost:5173`

## Runtime configuration
- Base package: `com.s1000Dorg.viewer`
- Main config file: `application/src/main/resources/application.yml`
- Config groups:
  - `spring.datasource`, `spring.jpa`, `spring.flyway`
  - `s1000d.storage`, `s1000d.ldap`, `s1000d.opa`
  - `viewer.render`, `viewer.applicability`, `viewer.policy`, `viewer.security`

Environment override examples:
- DB: `S1000D_DB_URL`, `S1000D_DB_USER`, `S1000D_DB_PASS`
- Storage: `S1000D_CSDB_ROOT`, `S1000D_PUBLISHED_ROOT`, `S1000D_CACHE_ROOT`, `S1000D_AUDIT_ROOT`
- LDAP: `S1000D_LDAP_URL`, `S1000D_LDAP_BASE_DN`, `S1000D_LDAP_MANAGER_DN`, `S1000D_LDAP_MANAGER_PASS`, `S1000D_LDAP_USER_DN_PATTERN`, `S1000D_LDAP_GROUP_SEARCH_BASE`, `S1000D_LDAP_GROUP_SEARCH_FILTER`
- OPA: `S1000D_OPA_ENABLED`, `S1000D_OPA_URL`, `S1000D_OPA_POLICY_PATH`, `S1000D_OPA_ALLOW_READ_ON_ERROR`

## Dev-auth credentials
Used only when profile `dev-auth` is enabled.
- `admin / ${VIEWER_DEMO_ADMIN_PASSWORD:-admin123}`
- `eng / ${VIEWER_DEMO_ENGINEER_PASSWORD:-eng123}`
- `view / ${VIEWER_DEMO_VIEWER_PASSWORD:-view123}`

## Docs
- `docs/how-to-run.md`
- `docs/local-oracle.md`
- `docs/local-enterprise-stack.md`
- `docs/applicability-phase1.md`
- `docs/applicability-phase2.md`
- `docs/applicability-phase3.md`

