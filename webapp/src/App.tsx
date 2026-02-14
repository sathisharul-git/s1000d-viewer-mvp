import { FormEvent, MouseEvent, useEffect, useMemo, useState } from "react";
import { api, authStorage } from "./api/client";
import { BreadcrumbBar } from "./components/breadcrumb/BreadcrumbBar";
import { ThreePaneLayout, type PaneWidths } from "./components/layout/ThreePaneLayout";
import { type ApplicabilityFilters, defaultApplicabilityFilters } from "./types/filters";
import type { Hotspot, ModuleListItem, ModuleRenderResponse, UserSummary } from "./types/models";

type ViewerLayoutState = {
  leftWidth: number;
  rightWidth: number;
  rightOpen: boolean;
};


const layoutStorageKey = "s1000d.viewer.layout.v3";
const defaultLayoutState: ViewerLayoutState = {
  leftWidth: 320,
  rightWidth: 360,
  rightOpen: false,
};

function hasRole(roles: string[], role: string): boolean {
  return roles.includes(role);
}

function escapeRegex(raw: string): string {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function readLayoutState(): ViewerLayoutState {
  if (typeof window === "undefined") {
    return defaultLayoutState;
  }

  try {
    const parsed = JSON.parse(window.localStorage.getItem(layoutStorageKey) ?? "");
    if (
      typeof parsed.leftWidth === "number" &&
      typeof parsed.rightWidth === "number" &&
      typeof parsed.rightOpen === "boolean"
    ) {
      return {
        leftWidth: Math.max(260, Math.min(560, parsed.leftWidth)),
        rightWidth: Math.max(300, Math.min(680, parsed.rightWidth)),
        rightOpen: parsed.rightOpen,
      };
    }
  } catch {
    return defaultLayoutState;
  }

  return defaultLayoutState;
}

function writeLayoutState(next: ViewerLayoutState): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(layoutStorageKey, JSON.stringify(next));
}

function highlightHtml(html: string, term: string): string {
  const trimmed = term.trim();
  if (!trimmed) {
    return html;
  }

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(`<div id="__s1000d-root">${html}</div>`, "text/html");
    const root = doc.getElementById("__s1000d-root");
    if (!root) {
      return html;
    }

    const regex = new RegExp(escapeRegex(trimmed), "gi");
    const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const textNodes: Text[] = [];

    while (walker.nextNode()) {
      const node = walker.currentNode as Text;
      if (node.nodeValue && node.nodeValue.trim().length > 0) {
        textNodes.push(node);
      }
    }

    for (const node of textNodes) {
      const value = node.nodeValue ?? "";
      if (!regex.test(value)) {
        regex.lastIndex = 0;
        continue;
      }
      regex.lastIndex = 0;

      const fragment = doc.createDocumentFragment();
      let last = 0;
      let match: RegExpExecArray | null;
      while ((match = regex.exec(value)) !== null) {
        if (match.index > last) {
          fragment.appendChild(doc.createTextNode(value.slice(last, match.index)));
        }
        const mark = doc.createElement("mark");
        mark.className = "search-hit";
        mark.textContent = match[0];
        fragment.appendChild(mark);
        last = match.index + match[0].length;
      }
      if (last < value.length) {
        fragment.appendChild(doc.createTextNode(value.slice(last)));
      }
      node.parentNode?.replaceChild(fragment, node);
    }

    return root.innerHTML;
  } catch {
    const regex = new RegExp(escapeRegex(trimmed), "gi");
    return html.replace(regex, (match) => `<mark class=\"search-hit\">${match}</mark>`);
  }
}

function detectGraphicId(target: HTMLElement): string {
  const graphicNode = target.closest<HTMLElement>("[data-icn-id], [data-icn], [data-icnref], [data-icn-ref]");
  if (graphicNode) {
    return (
      graphicNode.getAttribute("data-icn-id") ??
      graphicNode.getAttribute("data-icn") ??
      graphicNode.getAttribute("data-icnref") ??
      graphicNode.getAttribute("data-icn-ref") ??
      ""
    ).trim();
  }

  const link = target.closest<HTMLAnchorElement>("a[href]");
  const href = link?.getAttribute("href") ?? "";
  const hrefMatch = href.match(/(ICN-[A-Za-z0-9\-_.]+)/);
  return (hrefMatch?.[1] ?? "").trim();
}

export function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState(authStorage.readToken());
  const [userNameDisplay, setUserNameDisplay] = useState("");
  const [roles, setRoles] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const [catalog, setCatalog] = useState<ModuleListItem[]>([]);
  const [modules, setModules] = useState<ModuleListItem[]>([]);
  const [filters, setFilters] = useState<ApplicabilityFilters>(defaultApplicabilityFilters);
  const [moduleSearch, setModuleSearch] = useState("");

  const [selectedDmId, setSelectedDmId] = useState("");
  const [selectedContent, setSelectedContent] = useState<ModuleRenderResponse | null>(null);
  const [selectedGraphicId, setSelectedGraphicId] = useState("");

  const [graphicSvg, setGraphicSvg] = useState("");
  const [hotspots, setHotspots] = useState<Hotspot[]>([]);

  const [searchTerm, setSearchTerm] = useState("");
  const [layout, setLayout] = useState<ViewerLayoutState>(() => readLayoutState());

  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadAircraft, setUploadAircraft] = useState("");
  const [uploadEngine, setUploadEngine] = useState("");
  const [uploadVariant, setUploadVariant] = useState("");
  const [uploadIcnId, setUploadIcnId] = useState("");
  const [showUpload, setShowUpload] = useState(false);

  const [users, setUsers] = useState<UserSummary[]>([]);
  const [showUsers, setShowUsers] = useState(false);

  useEffect(() => {
    writeLayoutState(layout);
  }, [layout]);

  const aircraftOptions = useMemo(
    () => Array.from(new Set(catalog.flatMap((m) => m.applicability.aircraft))).sort(),
    [catalog],
  );

  const engineOptions = useMemo(
    () => Array.from(new Set(catalog.flatMap((m) => m.applicability.engine))).sort(),
    [catalog],
  );

  const variantOptions = useMemo(
    () => Array.from(new Set(catalog.flatMap((m) => m.applicability.variant))).sort(),
    [catalog],
  );

  const filteredModules = useMemo(() => {
    const needle = moduleSearch.trim().toLowerCase();
    if (!needle) {
      return modules;
    }
    return modules.filter(
      (module) => module.dmId.toLowerCase().includes(needle) || module.title.toLowerCase().includes(needle),
    );
  }, [moduleSearch, modules]);

  useEffect(() => {
    if (!token) {
      return;
    }
    api.me(token)
      .then((profile) => {
        setUserNameDisplay(profile.username);
        setRoles(profile.roles);
      })
      .catch(() => {
        logout();
      });
  }, [token]);

  useEffect(() => {
    if (!token) {
      return;
    }
    api.modules(token, defaultApplicabilityFilters)
      .then((response) => setCatalog(response.modules))
      .catch((err: unknown) => setError(String(err)));
  }, [token]);

  useEffect(() => {
    if (!token) {
      return;
    }
    api.modules(token, filters)
      .then((response) => {
        const rows = response.modules;
        setModules(rows);
        if (!rows.find((row) => row.dmId === selectedDmId)) {
          setSelectedDmId(rows[0]?.dmId ?? "");
        }
      })
      .catch((err: unknown) => setError(String(err)));
  }, [token, filters]);

  useEffect(() => {
    if (!token || !selectedDmId) {
      setSelectedContent(null);
      setSelectedGraphicId("");
      setGraphicSvg("");
      setHotspots([]);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const content = await api.moduleRender(token, selectedDmId, filters);
        if (cancelled) {
          return;
        }
        setSelectedContent(content);
        setSelectedGraphicId("");
        setGraphicSvg("");
        setHotspots([]);
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(String(err));
        setSelectedContent(null);
        setSelectedGraphicId("");
        setGraphicSvg("");
        setHotspots([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [filters, selectedDmId, token]);

  useEffect(() => {
    if (!token || !selectedGraphicId) {
      setGraphicSvg("");
      setHotspots([]);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const [svg, hs] = await Promise.all([api.graphic(token, selectedGraphicId), api.hotspots(token, selectedGraphicId)]);
        if (cancelled) {
          return;
        }
        setGraphicSvg(svg);
        setHotspots(hs);
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(String(err));
        setGraphicSvg("");
        setHotspots([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [selectedGraphicId, token]);

  const highlighted = useMemo(
    () => highlightHtml(selectedContent?.html ?? "<p>Select a data module to preview content.</p>", searchTerm),
    [searchTerm, selectedContent],
  );

  function logout() {
    setToken("");
    setRoles([]);
    setUserNameDisplay("");
    setSelectedContent(null);
    setGraphicSvg("");
    setHotspots([]);
    setSelectedGraphicId("");
    setSelectedDmId("");
    setCatalog([]);
    setModules([]);
    setShowUpload(false);
    setShowUsers(false);
    setUsers([]);
    authStorage.clear();
  }

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const response = await api.login(username, password);
      setToken(response.token);
      setUserNameDisplay(response.username);
      setRoles(response.roles);
      authStorage.writeToken(response.token);
      setUsername("");
      setPassword("");
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!token || !uploadFile) {
      setError("Select an XML file to upload.");
      return;
    }

    setError("");
    try {
      await api.upload(token, {
        file: uploadFile,
        title: uploadTitle,
        aircraft: uploadAircraft,
        engine: uploadEngine,
        variant: uploadVariant,
        icnId: uploadIcnId,
      });

      const [catalogRows, filteredRows] = await Promise.all([
        api.modules(token, defaultApplicabilityFilters),
        api.modules(token, filters),
      ]);
      setCatalog(catalogRows.modules);
      setModules(filteredRows.modules);

      setUploadFile(null);
      setUploadTitle("");
      setUploadAircraft("");
      setUploadEngine("");
      setUploadVariant("");
      setUploadIcnId("");
      setShowUpload(false);
    } catch (err) {
      setError(String(err));
    }
  }

  function openGraphic(icnId: string) {
    const normalized = icnId.trim();
    if (!normalized) {
      return;
    }
    setSelectedGraphicId(normalized);
    setLayout((prev) => ({ ...prev, rightOpen: true }));
  }

  function resolveModuleDmId(rawDmId: string): string {
    const normalized = rawDmId.trim();
    if (!normalized) {
      return "";
    }
    const fromVisible = modules.find((module) => module.dmId === normalized || module.dmId.startsWith(`${normalized}_`));
    if (fromVisible) {
      return fromVisible.dmId;
    }
    const fromCatalog = catalog.find((module) => module.dmId === normalized || module.dmId.startsWith(`${normalized}_`));
    if (fromCatalog) {
      return fromCatalog.dmId;
    }
    return normalized;
  }

  function handlePreviewClick(event: MouseEvent<HTMLDivElement>) {
    const target = event.target as HTMLElement | null;
    if (!target) {
      return;
    }

    const icnId = detectGraphicId(target);
    if (icnId) {
      event.preventDefault();
      openGraphic(icnId);
      return;
    }

    const dmLink = target.closest<HTMLElement>("[data-dm-id], [data-dmref]");
    if (dmLink) {
      const dmId = (dmLink.getAttribute("data-dm-id") ?? dmLink.getAttribute("data-dmref") ?? "").trim();
      if (dmId) {
        event.preventDefault();
        setFilters(defaultApplicabilityFilters);
        setSelectedDmId(resolveModuleDmId(dmId));
      }
    }
  }

  async function loadUsers() {
    if (!token) {
      return;
    }
    try {
      setUsers(await api.users(token));
      setShowUsers(true);
    } catch (err) {
      setError(String(err));
    }
  }

  function handlePaneResize(next: PaneWidths) {
    setLayout((prev) => ({
      ...prev,
      leftWidth: next.leftWidth,
      rightWidth: next.rightWidth,
    }));
  }

  if (!token) {
    return (
      <div className="login-page">
        <div className="login-panel">
          <h1>S1000D Viewer</h1>
          <p>Role-aware viewer with applicability filtering and hotspot navigation.</p>
          <form onSubmit={handleLogin}>
            <label>
              Username
              <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="admin / eng / view" />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="admin123 / eng123 / view123"
              />
            </label>
            <button disabled={busy} type="submit">
              {busy ? "Signing in..." : "Login"}
            </button>
          </form>
          {error ? <div className="error">{error}</div> : null}
        </div>
      </div>
    );
  }

  const canUpload = hasRole(roles, "ROLE_ADMIN") || hasRole(roles, "ROLE_ENGINEER");
  const isAdmin = hasRole(roles, "ROLE_ADMIN");
  const selectedDmLabel = selectedContent?.meta.title || selectedDmId || "No module selected";

  return (
    <div className="app-shell">
      <header className="top-banner">
        <div>
          <h1>S1000D Viewer</h1>
          <p>{userNameDisplay} ({roles.join(", ")})</p>
        </div>
        <div className="top-actions">
          {canUpload ? (
            <button type="button" className={showUpload ? "action-btn active" : "action-btn"} onClick={() => setShowUpload((open) => !open)}>
              {showUpload ? "Hide Upload" : "Upload Module"}
            </button>
          ) : null}
          {isAdmin ? (
            <button
              type="button"
              className={showUsers ? "action-btn active" : "action-btn"}
              onClick={() => {
                if (showUsers) {
                  setShowUsers(false);
                } else {
                  void loadUsers();
                }
              }}
            >
              {showUsers ? "Hide Users" : "Load Users"}
            </button>
          ) : null}
          <button type="button" className="action-btn danger" onClick={logout}>
            Logout
          </button>
        </div>
      </header>

      <BreadcrumbBar
        selectedDmLabel={selectedDmLabel}
        selectedGraphicId={selectedGraphicId}
        filters={filters}
        aircraftOptions={aircraftOptions}
        engineOptions={engineOptions}
        variantOptions={variantOptions}
        onApplyFilters={setFilters}
      />

      {error ? <div className="error floating">{error}</div> : null}

      {showUpload && canUpload ? (
        <section className="utility-panel">
          <form className="upload-grid" onSubmit={handleUpload}>
            <input type="file" accept=".xml" onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)} />
            <input placeholder="Title" value={uploadTitle} onChange={(e) => setUploadTitle(e.target.value)} />
            <input placeholder="Aircraft" value={uploadAircraft} onChange={(e) => setUploadAircraft(e.target.value)} />
            <input placeholder="Engine" value={uploadEngine} onChange={(e) => setUploadEngine(e.target.value)} />
            <input placeholder="Variant" value={uploadVariant} onChange={(e) => setUploadVariant(e.target.value)} />
            <input placeholder="ICN ID" value={uploadIcnId} onChange={(e) => setUploadIcnId(e.target.value)} />
            <button type="submit">Upload</button>
          </form>
        </section>
      ) : null}

      {showUsers && isAdmin ? (
        <section className="utility-panel users-panel">
          <div className="users-list">
            {users.map((u) => (
              <div key={u.username} className="user-row">
                <strong>{u.username}</strong>
                <span>{u.roles.join(", ")}</span>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <ThreePaneLayout
        leftWidth={layout.leftWidth}
        rightWidth={layout.rightWidth}
        rightCollapsed={!layout.rightOpen}
        onWidthsChange={handlePaneResize}
        left={
          <aside className="panel">
            <div className="panel-title">Data Modules</div>
            <div className="left-module-controls">
              <input
                className="module-search"
                value={moduleSearch}
                onChange={(event) => setModuleSearch(event.target.value)}
                placeholder="Filter module names"
              />
            </div>

            <div className="module-list names-only">
              {filteredModules.map((module) => (
                <button
                  key={module.dmId}
                  type="button"
                  className={module.dmId === selectedDmId ? "module-row active" : "module-row"}
                  onClick={() => setSelectedDmId(module.dmId)}
                  title={module.title}
                >
                  {module.dmId}
                </button>
              ))}
            </div>
            <div className="module-footnote">{filteredModules.length} modules</div>
          </aside>
        }
        center={
          <main className="panel">
            <div className="panel-title">Preview</div>
            <input
              className="search"
              placeholder="Search and highlight text"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
            {selectedContent ? (
              <div className="preview-meta">
                <span className="meta-chip">Source: {selectedContent.source}</span>
                <span className={`applicability-badge ${selectedContent.meta.applicabilityResult.toLowerCase()}`}>
                  {selectedContent.meta.applicabilityResult}
                </span>
                <span className="meta-note" title={selectedContent.meta.applicabilityReason}>
                  {selectedContent.meta.applicabilityReason}
                </span>
              </div>
            ) : null}

            {selectedContent?.assets.icns.length ? (
              <div className="preview-assets">
                {selectedContent.assets.icns.map((icnId) => (
                  <button
                    key={icnId}
                    type="button"
                    className={icnId === selectedGraphicId ? "asset-btn active" : "asset-btn"}
                    onClick={() => openGraphic(icnId)}
                  >
                    Open Graphic: {icnId}
                  </button>
                ))}
              </div>
            ) : null}

            <div className="preview dm-rendered" onClick={handlePreviewClick} dangerouslySetInnerHTML={{ __html: highlighted }} />
          </main>
        }
        right={
          <aside className={layout.rightOpen ? "panel right-panel" : "panel right-panel collapsed"}>
            <div className="right-panel-header">
              <div className="panel-title">Graphics</div>
              <button
                type="button"
                className="collapse-btn"
                onClick={() =>
                  setLayout((prev) => ({
                    ...prev,
                    rightOpen: !prev.rightOpen,
                  }))
                }
                title={layout.rightOpen ? "Collapse panel" : "Open panel"}
              >
                {layout.rightOpen ? "Collapse" : "Open"}
              </button>
            </div>

            {layout.rightOpen ? (
              <>
                <div className="graphic-meta">
                  {selectedGraphicId ? `Selected graphic: ${selectedGraphicId}` : "Select a graphic from preview to load SVG."}
                </div>
                {!selectedGraphicId ? (
                  <div className="graphics-empty">Select a graphic in Preview to enable this panel.</div>
                ) : (
                  <div className="graphic-wrap">
                    {graphicSvg ? <div className="graphic-svg" dangerouslySetInnerHTML={{ __html: graphicSvg }} /> : <p>No image available.</p>}
                    {hotspots.map((hotspot) => (
                      <button
                        key={hotspot.id}
                        className="hotspot"
                        style={{
                          left: `${hotspot.x}%`,
                          top: `${hotspot.y}%`,
                          width: `${hotspot.w}%`,
                          height: `${hotspot.h}%`,
                        }}
                        title={hotspot.label}
                        onClick={() => {
                          if (hotspot.targetDmId) {
                            setFilters(defaultApplicabilityFilters);
                            setSelectedDmId(resolveModuleDmId(hotspot.targetDmId));
                          }
                        }}
                      >
                        <span>{hotspot.label}</span>
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="graphics-collapsed-hint">Graphics</div>
            )}
          </aside>
        }
      />
    </div>
  );
}
