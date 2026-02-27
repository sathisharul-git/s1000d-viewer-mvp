# Applicability Phase 1 Contract

Phase 1 uses DM-level applicability tags with deterministic matching.

## Sidecar file
Path: `data/csdb/meta/<dmId>.json`

```json
{
  "dmId": "DMC-EXAMPLE-AAA-00-00-00-00A-040A-C",
  "applicability": {
    "aircraft": ["A320", "A321"],
    "engine": ["LEAP-1A"],
    "variant": ["MOD-12"]
  }
}
```

## Published manifest section
Path: `data/published/manifest.json`

`modules[].applicability` uses the same keys (`aircraft`, `engine`, `variant`).

## Source precedence
1. published manifest
2. csdb sidecar meta
3. DM header `<dmStatus><applic>` (if metadata is unavailable)
4. none

When sidecar metadata contains serialized `<applic>` XML (for example in a property block), that XML is evaluated before list-based tags.

## Unknown handling
If a requested dimension is missing in DM applicability tags, that dimension evaluates to `UNKNOWN`.

## Inline applicability (current baseline)
- Render now evaluates inline fragment references (`applicRefId`/`applicRef`) against local `<applic id="...">` definitions.
- Non-applicable inline fragments are removed in quick preview mode.
- Response includes summary:
  - `inlineApplicability.mode` (`FILTERED` or `NONE`)
  - `inlineApplicability.removedCount`
  - `inlineApplicability.keptCount`
