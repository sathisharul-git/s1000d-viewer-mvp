# Applicability Phase 3 (Policy Engine) Plan

Phase 3 is a skeleton only. It defines policy and BREX boundaries for defence-grade control without adding runtime dependencies.

## Goal
- Move from tag matching to policy decisions (ABAC-style) across DM and fragment scopes.
- Align applicability decisions with BREX and compliance constraints.

## Current extension points
- `ApplicabilityRuleEngine`
- `PolicyDecisionPoint` (PDP boundary)
- `BrexValidator`
- `Phase3ApplicabilityRuleEngine`
- Feature flag: `applicability.phase3.enabled=false`

## Intended integration points
1. DM-level decision before render/list exposure.
2. Fragment-level decision during render pipeline (after Phase 2 AST evaluation).
3. Optional external PDP adapter (example: OPA) through `PolicyDecisionPoint`.
4. BREX profile checks and policy constraints through `BrexValidator`.

## Defence-focused policy notes
- Export-control and classification checks belong in PDP policy bundles.
- Role + mission + platform attributes can be enforced as ABAC inputs.
- Enforcement points should emit audit-ready reason codes and decision traces.
