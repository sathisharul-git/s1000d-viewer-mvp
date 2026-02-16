import { type KeyboardEvent, type ReactNode, useMemo, useRef } from "react";

const SPLITTER_WIDTH = 8;

export type PaneWidths = {
  leftWidth: number;
  rightWidth: number;
};

type ThreePaneLayoutProps = {
  left: ReactNode;
  center: ReactNode;
  right: ReactNode;
  leftWidth: number;
  rightWidth: number;
  rightCollapsed: boolean;
  minLeftWidth?: number;
  minCenterWidth?: number;
  minRightWidth?: number;
  collapsedRightWidth?: number;
  onWidthsChange: (next: PaneWidths) => void;
};

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function ThreePaneLayout({
  left,
  center,
  right,
  leftWidth,
  rightWidth,
  rightCollapsed,
  minLeftWidth = 260,
  minCenterWidth = 460,
  minRightWidth = 300,
  collapsedRightWidth = 56,
  onWidthsChange,
}: ThreePaneLayoutProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const activeRightWidth = rightCollapsed ? collapsedRightWidth : rightWidth;

  const templateColumns = useMemo(
    () => `${leftWidth}px ${SPLITTER_WIDTH}px minmax(${minCenterWidth}px, 1fr) ${SPLITTER_WIDTH}px ${activeRightWidth}px`,
    [activeRightWidth, leftWidth, minCenterWidth],
  );

  function resizeLeft(nextLeft: number) {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const totalWidth = container.getBoundingClientRect().width;
    const maxLeft = Math.max(minLeftWidth, totalWidth - activeRightWidth - minCenterWidth - SPLITTER_WIDTH * 2);
    const boundedLeft = clamp(nextLeft, minLeftWidth, maxLeft);
    onWidthsChange({ leftWidth: boundedLeft, rightWidth });
  }

  function resizeRight(nextRight: number) {
    if (rightCollapsed) {
      return;
    }
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const totalWidth = container.getBoundingClientRect().width;
    const maxRight = Math.max(minRightWidth, totalWidth - leftWidth - minCenterWidth - SPLITTER_WIDTH * 2);
    const boundedRight = clamp(nextRight, minRightWidth, maxRight);
    onWidthsChange({ leftWidth, rightWidth: boundedRight });
  }

  function startLeftDrag(startX: number) {
    const startLeft = leftWidth;
    const onMove = (event: PointerEvent) => resizeLeft(startLeft + (event.clientX - startX));
    const onUp = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp, { once: true });
  }

  function startRightDrag(startX: number) {
    if (rightCollapsed) {
      return;
    }
    const startRight = rightWidth;
    const onMove = (event: PointerEvent) => resizeRight(startRight - (event.clientX - startX));
    const onUp = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp, { once: true });
  }

  function handleLeftSplitterKey(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      resizeLeft(leftWidth - 16);
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      resizeLeft(leftWidth + 16);
    }
  }

  function handleRightSplitterKey(event: KeyboardEvent<HTMLDivElement>) {
    if (rightCollapsed) {
      return;
    }
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      resizeRight(rightWidth + 16);
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      resizeRight(rightWidth - 16);
    }
  }

  return (
    <section ref={containerRef} className="three-pane-layout" style={{ gridTemplateColumns: templateColumns }}>
      {left}
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize data modules and preview panels"
        tabIndex={0}
        className="pane-splitter"
        onPointerDown={(event) => {
          event.preventDefault();
          startLeftDrag(event.clientX);
        }}
        onKeyDown={handleLeftSplitterKey}
      />
      {center}
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize preview and graphics panels"
        aria-disabled={rightCollapsed}
        tabIndex={rightCollapsed ? -1 : 0}
        className={rightCollapsed ? "pane-splitter disabled" : "pane-splitter"}
        onPointerDown={(event) => {
          event.preventDefault();
          startRightDrag(event.clientX);
        }}
        onKeyDown={handleRightSplitterKey}
      />
      {right}
    </section>
  );
}
