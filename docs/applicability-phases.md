# Applicability Phases

## Phase 1 (Implemented)
Scope: DM-level applicability filtering.

### Data source priority
1. `data/published/manifest.json` (`modules[].applicability`)
2. `data/csdb/meta/<dmId>.json` (`applicability.aircraft[]`, `applicability.engine[]`, `applicability.variant[]`)
3. Unknown applicability when neither source provides values

### Evaluation behavior
- Filter dimensions: `aircraft`, `engine`, `variant`
- API returns explainable decisions: `APPLICABLE | NOT_APPLICABLE | UNKNOWN`
- `NOT_APPLICABLE` is filtered out in `/api/modules`
- `UNKNOWN` remains visible in `/api/modules`
- `/api/modules/{dmId}/render` still renders and tags applicability in metadata

## Phase 2 (Skeleton only)
Scope: Section-level applicability inside a DM.

### Extension points already added
- `ApplicabilityExpressionParser`
- `ApplicabilityExpression` AST
- `ApplicabilityEvaluator`
- `DefaultApplicabilityEvaluator`
- Config flag: `viewer.applicability.fragmentEvaluation.enabled` (default `false`)

### Intended approach
- Parse applicability markup into expression trees
- Evaluate expression per section against request context
- Remove or mark non-applicable nodes in renderer

## Phase 3 (Skeleton only)
Scope: Full applicability engine with richer product model and BREX alignment.

### Extension points already added
- `ApplicabilityRuleEngine`
- `PolicyDecisionPoint`
- `PolicyDecision`
- `BrexValidator`
- `PolicyDrivenApplicabilityRuleEngine`
- `NoOpBrexValidator`
- Config flag: `viewer.policy.enforcement.enabled` (default `false`)

### Intended approach
- Integrate product model catalogs and rule sets
- Evaluate composite applicability expressions across dimensions
- Add BREX compliance checks during validation/render workflows

See `docs/applicability-phase1.md`, `docs/applicability-phase2.md`, and `docs/applicability-phase3.md` for phase-specific details.
