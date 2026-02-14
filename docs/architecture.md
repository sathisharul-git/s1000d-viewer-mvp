# Architecture Overview

## Modules
- `application` (Spring Boot backend)
  - `domain/` stable core types (`Applicability`, `DataModuleDescriptor`)
  - `render/` render orchestration (`RenderFacade`, `PublishedRenderService`, `QuickRenderService`, `RenderCache`)
  - `applicability/` DM-level applicability service + provider + matcher
  - `applicability/eval/` fragment-evaluation extension interfaces
  - `policy/` policy/BREX enforcement extension interfaces
  - `adapters/fs/` file-system repository for CSDB and published data
- `webapp` (React + TypeScript frontend)

## Rendering strategy
1. `PublishedRenderService`
   - Reads `data/published/dm/<dmId>/index.html`
   - Source returned as `published`
2. `QuickRenderService`
   - Reads `data/csdb/dm/<dmId>.xml`
   - Converts XML to HTML using `application/src/main/resources/xslt/s1000d-dm-to-html.xsl`
   - Source returned as `quick`

`RenderFacade` uses config-driven source selection:
- `viewer.render.publishedPreferred`
- `viewer.render.quickPreviewEnabled`

## Applicability (phased)
- Phase 1 implemented now:
  - Provider priority:
    1. `data/published/manifest.json`
    2. `data/csdb/meta/<dmId>.json`
    3. unknown
  - Unknown applicability is included in filtered module lists and tagged as `UNKNOWN`.
- Phase 2 skeleton:
  - `applicability.eval.ApplicabilityExpressionParser`
  - `applicability.eval.ApplicabilityEvaluator`
  - Feature flag: `viewer.applicability.fragmentEvaluation.enabled`
- Phase 3 skeleton:
  - `policy.PolicyDecisionPoint`
  - `policy.BrexValidator`
  - Feature flag: `viewer.policy.enforcement.enabled`

## Data adapters
`FsDataRepository` standardizes all file locations and prevents path-scattered logic in controllers/services.

## Security
- JWT auth with Spring Security
- Upload endpoint restricted to ADMIN/ENGINEER
- Admin endpoints restricted to ADMIN

## Caching
- `DefaultRenderCache`: in-memory + best-effort disk cache at configured `viewer.storage.cacheRoot`.
- TTL is controlled by `viewer.render.cacheTtlSeconds`.
