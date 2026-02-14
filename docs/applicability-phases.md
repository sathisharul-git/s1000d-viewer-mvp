# Applicability Phases

## Phase 1 (Implemented)
Scope: DM-level applicability filtering.

### Data source priority
1. `data/published/manifest.json` (`modules[].applicability`)
2. `data/csdb/meta/<dmId>.json` (`applicability.aircraft[]`, `applicability.engine[]`)
3. Unknown applicability when neither source provides values

### Evaluation behavior
- Filter dimensions: `aircraft`, `engine`
- If filter value is present and DM list contains it -> match
- If DM applicability is unknown -> include in list and tag as `UNKNOWN`
- Render response returns `APPLICABLE | NOT_APPLICABLE | UNKNOWN`

## Phase 2 (Skeleton only)
Scope: Section-level applicability inside a DM.

### Extension points already added
- `ApplicabilityEvaluator`
- `Phase2SectionApplicabilityEvaluator`
- Config flag: `s1000d.phase2-section-applicability-enabled` (default `false`)

### Intended approach
- Parse applicability markup into expression trees
- Evaluate expression per section against request context
- Remove or mark non-applicable nodes in renderer

## Phase 3 (Skeleton only)
Scope: Full applicability engine with richer product model and BREX alignment.

### Extension points already added
- `ApplicabilityRuleEngine`
- `BrexValidator`
- `Phase3ApplicabilityRuleEngine`
- `NoOpBrexValidator`
- Config flag: `s1000d.phase3-rule-engine-enabled` (default `false`)

### Intended approach
- Integrate product model catalogs and rule sets
- Evaluate composite applicability expressions across dimensions
- Add BREX compliance checks during validation/render workflows
