import { type RefObject, useEffect, useRef, useState } from "react";
import { type ApplicabilityFilters, defaultApplicabilityFilters } from "../../types/filters";

type ApplicabilityFilterPopoverProps = {
  open: boolean;
  anchorRef: RefObject<HTMLButtonElement>;
  filters: ApplicabilityFilters;
  aircraftOptions: string[];
  engineOptions: string[];
  variantOptions: string[];
  onApply: (next: ApplicabilityFilters) => void;
  onClose: (options?: { restoreFocus?: boolean }) => void;
};

export function ApplicabilityFilterPopover({
  open,
  anchorRef,
  filters,
  aircraftOptions,
  engineOptions,
  variantOptions,
  onApply,
  onClose,
}: ApplicabilityFilterPopoverProps) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const firstSelectRef = useRef<HTMLSelectElement | null>(null);
  const [draftFilters, setDraftFilters] = useState<ApplicabilityFilters>(filters);

  useEffect(() => {
    if (!open) {
      return;
    }
    setDraftFilters(filters);
    window.requestAnimationFrame(() => {
      firstSelectRef.current?.focus();
    });
  }, [filters, open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    const handleOutsidePointer = (event: MouseEvent | TouchEvent) => {
      const targetNode = event.target as Node | null;
      if (!targetNode) {
        return;
      }
      if (panelRef.current?.contains(targetNode)) {
        return;
      }
      if (anchorRef.current?.contains(targetNode)) {
        return;
      }
      onClose({ restoreFocus: true });
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") {
        return;
      }
      event.preventDefault();
      onClose({ restoreFocus: true });
    };

    window.addEventListener("mousedown", handleOutsidePointer);
    window.addEventListener("touchstart", handleOutsidePointer, { passive: true });
    window.addEventListener("keydown", handleEscape);

    return () => {
      window.removeEventListener("mousedown", handleOutsidePointer);
      window.removeEventListener("touchstart", handleOutsidePointer);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [anchorRef, onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <div className="filter-popover" ref={panelRef} role="dialog" aria-label="Applicability filters" aria-modal="false">
      <div className="filter-popover-header">
        <h2>Applicability Filters</h2>
        <button
          type="button"
          className="filter-popover-close"
          onClick={() => onClose({ restoreFocus: true })}
          aria-label="Close filters"
        >
          x
        </button>
      </div>

      <div className="filter-popover-body">
        <label>
          Aircraft
          <select
            ref={firstSelectRef}
            value={draftFilters.aircraft}
            onChange={(event) => setDraftFilters((prev) => ({ ...prev, aircraft: event.target.value }))}
          >
            <option value="">All</option>
            {aircraftOptions.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>

        <label>
          Engine
          <select value={draftFilters.engine} onChange={(event) => setDraftFilters((prev) => ({ ...prev, engine: event.target.value }))}>
            <option value="">All</option>
            {engineOptions.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>

        <label>
          Variant
          <select
            value={draftFilters.variant}
            onChange={(event) => setDraftFilters((prev) => ({ ...prev, variant: event.target.value }))}
          >
            <option value="">All</option>
            {variantOptions.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="filter-popover-actions">
        <button type="button" className="ghost-btn" onClick={() => setDraftFilters(defaultApplicabilityFilters)}>
          Reset
        </button>
        <button
          type="button"
          onClick={() => {
            onApply(draftFilters);
            onClose({ restoreFocus: true });
          }}
        >
          Apply
        </button>
      </div>
    </div>
  );
}
