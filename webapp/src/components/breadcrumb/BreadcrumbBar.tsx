import { useCallback, useRef, useState } from "react";
import { ApplicabilityFilterPopover } from "../filters/ApplicabilityFilterPopover";
import type { ApplicabilityFilters } from "../../types/filters";
import type { PmcListItem } from "../../types/models";

type BreadcrumbBarProps = {
  pmcs: PmcListItem[];
  selectedPmcId: string;
  onSelectPmc: (pmcId: string) => void;
  selectedDmLabel: string;
  selectedGraphicId: string;
  filters: ApplicabilityFilters;
  aircraftOptions: string[];
  engineOptions: string[];
  variantOptions: string[];
  onApplyFilters: (next: ApplicabilityFilters) => void;
  canUpload: boolean;
  showUpload: boolean;
  onToggleUpload: () => void;
  canReindex: boolean;
  reindexBusy: boolean;
  onReindex: () => void;
};

export function BreadcrumbBar({
  pmcs,
  selectedPmcId,
  onSelectPmc,
  selectedDmLabel,
  selectedGraphicId,
  filters,
  aircraftOptions,
  engineOptions,
  variantOptions,
  onApplyFilters,
  canUpload,
  showUpload,
  onToggleUpload,
  canReindex,
  reindexBusy,
  onReindex,
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
          <span>Publications</span>
          <span className="separator">/</span>
          <span className="current">{selectedPmcId || "No PMC selected"}</span>
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
          <select
            className="breadcrumb-pmc-select"
            value={selectedPmcId}
            onChange={(event) => onSelectPmc(event.target.value)}
            title="Select publication module"
          >
            {pmcs.length === 0 ? <option value="">No PMC</option> : null}
            {pmcs.map((pmc) => (
              <option key={pmc.pmcId} value={pmc.pmcId}>
                {pmc.title ? `${pmc.pmcId} - ${pmc.title}` : pmc.pmcId}
              </option>
            ))}
          </select>
          {canUpload ? (
            <button
              type="button"
              className="breadcrumb-upload-btn"
              onClick={onToggleUpload}
              title={showUpload ? "Hide upload panel" : "Show upload panel"}
            >
              {showUpload ? "Hide Upload" : "Upload"}
            </button>
          ) : null}
          {canReindex ? (
            <button
              type="button"
              className="breadcrumb-reindex-btn"
              onClick={onReindex}
              disabled={reindexBusy}
              title="Reindex imported CSDB files on demand"
            >
              {reindexBusy ? "Reindexing..." : "Reindex"}
            </button>
          ) : null}
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
