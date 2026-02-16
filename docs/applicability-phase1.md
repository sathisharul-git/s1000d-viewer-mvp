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
3. none

## Unknown handling
If a requested dimension is missing in DM applicability tags, that dimension evaluates to `UNKNOWN`.
