import { useCallback, useRef, useState } from "react";
import { ApplicabilityFilterPopover } from "../filters/ApplicabilityFilterPopover";
import type { ApplicabilityFilters } from "../../types/filters";

type BreadcrumbBarProps = {
  selectedDmLabel: string;
  selectedGraphicId: string;
  filters: ApplicabilityFilters;
  aircraftOptions: string[];
  engineOptions: string[];
  variantOptions: string[];
  onApplyFilters: (next: ApplicabilityFilters) => void;
};

export function BreadcrumbBar({
  selectedDmLabel,
  selectedGraphicId,
  filters,
  aircraftOptions,
  engineOptions,
  variantOptions,
  onApplyFilters,
}: BreadcrumbBarProps) {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const [popoverOpen, setPopoverOpen] = useState(false);

  const closePopover = useCallback((options?: { restoreFocus?: boolean }) => {
    setPopoverOpen(false);
    if (options?.restoreFocus) {
      window.requestAnimationFrame(() => {
        buttonRef.current?.focus();
      });
    }
  }, []);

  const applicabilitySummary = [
    filters.aircraft || "All",
    filters.engine || "All",
    filters.variant || "All",
  ].join(" | ");

  return (
    <section className="viewer-breadcrumb">
      <div className="breadcrumb-row">
        <div className="breadcrumb-path">
          <span>Home</span>
          <span className="separator">/</span>
          <span>Data Modules</span>
          <span className="separator">/</span>
          <span className="current">{selectedDmLabel}</span>
          {selectedGraphicId ? (
            <>
              <span className="separator">/</span>
              <span className="current">{selectedGraphicId}</span>
            </>
          ) : null}
        </div>

        <div className="breadcrumb-actions">
          <button
            ref={buttonRef}
            type="button"
            className="breadcrumb-filter-btn"
            aria-haspopup="dialog"
            aria-expanded={popoverOpen}
            onClick={() => setPopoverOpen((open) => !open)}
          >
            Filters
          </button>
          <ApplicabilityFilterPopover
            open={popoverOpen}
            anchorRef={buttonRef}
            filters={filters}
            aircraftOptions={aircraftOptions}
            engineOptions={engineOptions}
            variantOptions={variantOptions}
            onApply={onApplyFilters}
            onClose={closePopover}
          />
        </div>
      </div>
      <div className="breadcrumb-context" title={`Aircraft=${filters.aircraft || "All"}, Engine=${filters.engine || "All"}, Variant=${filters.variant || "All"}`}>
        Applicability: {applicabilitySummary}
      </div>
    </section>
  );
}
