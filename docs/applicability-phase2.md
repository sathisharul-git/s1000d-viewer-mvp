# Applicability Phase 2 (Section-Level) Plan

Phase 2 is a skeleton only in this repository. Runtime behavior is unchanged unless explicitly enabled later.

## Goal
- Evaluate applicability at section/fragment level inside a DM.
- Allow renderer to hide or annotate non-applicable content based on request context.

## Current extension points
- `ApplicabilityExpressionParser`
- `ApplicabilityExpression` (AST placeholder)
- `ApplicabilityEvaluator`
- `Phase2SectionApplicabilityEvaluator`
- Feature flag: `applicability.phase2.enabled=false`

## Intended flow
1. Parse S1000D applicability markup from DM XML.
2. Build an AST (`ApplicabilityExpression`) for each scoped fragment.
3. Evaluate AST against `ApplicabilityContext` (aircraft/engine/variant/...).
4. Renderer removes or marks non-applicable nodes.

## Implementation notes
- Parser and evaluator are TODO stubs by design.
- AST model is intentionally small and can be expanded as expression coverage grows.
- Keep parser/evaluator isolated from render code to allow replacement without refactoring core services.
