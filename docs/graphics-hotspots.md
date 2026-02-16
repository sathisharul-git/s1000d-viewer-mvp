# CGM Hotspot Overlay Mapping

This project keeps the visible CGM rendering unchanged and emits hotspot overlays as an additional SVG layer.

## Output Contract

When a CGM contains hotspot metadata (APS/ASA attributes such as `region`, `name`, `linkuri`, `screentip`), the converter adds:

```xml
<g id="hotspot-overlay-layer" pointer-events="all">
  <g class="s1000d-hotspot"
     data-hotspot-id="..."
     data-aps-name="..."
     data-linkuri="..."
     data-screentip="...">
    <polygon|rect class="s1000d-hotspot-shape"
                  fill-opacity="0"
                  stroke-opacity="0"
                  pointer-events="all"/>
  </g>
</g>
```

- The visible drawing stays unchanged.
- Overlay shapes are non-visible by default and can be highlighted by UI.
- If no hotspots exist, no hotspot overlay layer is emitted.

## Identifier Stability

Hotspot IDs are deterministic:

1. Prefer explicit attribute identifiers (`name`/`id`) when available.
2. Fallback to APS identifier.
3. If missing, derive `hs-<stableHash(apsIndex + geometrySignature)>`.

If preferred IDs collide, a deterministic hash suffix is appended.

## Region Geometry

Region geometry from CGM `region` ASA is translated to SVG in the same viewBox space used by the rendered image:

- Rectangle-like regions -> `<rect>`
- Other closed/open regions -> `<polygon>`

Coordinates are mapped from CGM extent to output image width/height.

## UI Highlight Hook

UI can highlight hotspot overlays using:

```js
document.querySelector(`[data-hotspot-id="${id}"]`)
```

Recommended CSS behavior:

- default: invisible and pointer-interactive
- highlighted: apply `is-highlighted` class on `.s1000d-hotspot`

## Webapp Two-Way Sync Contract

The viewer keeps hotspot IDs aligned between Preview and SVG:

- Preview hotspot rows carry:
  - `data-icn-id="ICN-..."`
  - `data-hotspot-id="<stable hotspot id>"`
- SVG hotspot wrappers carry:
  - `data-hotspot-id="<stable hotspot id>"`

Matching normalization:

1. Trim whitespace
2. Remove optional leading `#`
3. Match by exact ID value

Highlight classes used by the webapp:

- SVG side:
  - `.hotspot-highlighted` on hotspot wrapper/shape
  - `.hotspot-highlight-rect` for red bounding rectangle overlay
- Preview side:
  - `.hotspot-link-selected` on selected hotspot row
