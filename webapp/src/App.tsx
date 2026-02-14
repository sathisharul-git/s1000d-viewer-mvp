import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { api, authStorage } from "./api/client";
import type { Hotspot, ModuleListItem, ModuleRenderResponse, UserSummary } from "./types/models";

type ApplicabilityFilters = {
  aircraft: string;
  engine: string;
};

const defaultFilters: ApplicabilityFilters = {
  aircraft: "",
  engine: "",
};

function hasRole(roles: string[], role: string): boolean {
  return roles.includes(role);
}

function escapeRegex(raw: string): string {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function formatApplicability(values: string[]): string {
  return values.length > 0 ? values.join(", ") : "UNKNOWN";
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
  const [filters, setFilters] = useState<ApplicabilityFilters>(defaultFilters);
  const [selectedDmId, setSelectedDmId] = useState("");
  const [selectedContent, setSelectedContent] = useState<ModuleRenderResponse | null>(null);

  const [graphicSvg, setGraphicSvg] = useState("");
  const [hotspots, setHotspots] = useState<Hotspot[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [activeIcnId, setActiveIcnId] = useState("");

  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadAircraft, setUploadAircraft] = useState("");
  const [uploadEngine, setUploadEngine] = useState("");
  const [uploadIcnId, setUploadIcnId] = useState("");
  const [showUpload, setShowUpload] = useState(false);

  const [users, setUsers] = useState<UserSummary[]>([]);
  const rightPanelRef = useRef<HTMLElement | null>(null);

  const aircraftOptions = useMemo(
    () => Array.from(new Set(catalog.flatMap((m) => m.applicability.aircraft))).sort(),
    [catalog],
  );

  const engineOptions = useMemo(
    () => Array.from(new Set(catalog.flatMap((m) => m.applicability.engine))).sort(),
    [catalog],
  );

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
    api.modules(token, defaultFilters)
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
  }, [token, filters, selectedDmId]);

  useEffect(() => {
    if (!token || !selectedDmId) {
      setSelectedContent(null);
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
        setActiveIcnId(content.assets.icns[0] ?? "");
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(String(err));
        setSelectedContent(null);
        setGraphicSvg("");
        setHotspots([]);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token, selectedDmId, filters]);

  useEffect(() => {
    if (!token || !activeIcnId) {
      setGraphicSvg("");
      setHotspots([]);
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const [svg, hs] = await Promise.all([
          api.graphic(token, activeIcnId),
          api.hotspots(token, activeIcnId),
        ]);
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
  }, [token, activeIcnId]);

  const highlighted = useMemo(
    () => highlightHtml(selectedContent?.html ?? "<p>Select a data module to preview content.</p>", searchTerm),
    [selectedContent, searchTerm],
  );

  function logout() {
    setToken("");
    setRoles([]);
    setUserNameDisplay("");
    setSelectedContent(null);
    setGraphicSvg("");
    setHotspots([]);
    setShowUpload(false);
    setActiveIcnId("");
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
        icnId: uploadIcnId,
      });

      const [catalogRows, filteredRows] = await Promise.all([
        api.modules(token, defaultFilters),
        api.modules(token, filters),
      ]);
      setCatalog(catalogRows.modules);
      setModules(filteredRows.modules);

      setUploadFile(null);
      setUploadTitle("");
      setUploadAircraft("");
      setUploadEngine("");
      setUploadIcnId("");
      setShowUpload(false);
    } catch (err) {
      setError(String(err));
    }
  }

  function activateGraphic(icnId: string) {
    const value = icnId.trim();
    if (!value) {
      return;
    }
    setActiveIcnId(value);
    rightPanelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function resolveModuleDmId(rawDmId: string): string {
    const normalized = rawDmId.trim();
    if (!normalized) {
      return "";
    }

    const exact = modules.find((module) => module.dmId === normalized);
    if (exact) {
      return exact.dmId;
    }

    const prefix = modules.find((module) => module.dmId.startsWith(`${normalized}_`));
    if (prefix) {
      return prefix.dmId;
    }

    return normalized;
  }

  function handlePreviewClick(event: MouseEvent<HTMLDivElement>) {
    const target = event.target as HTMLElement | null;
    if (!target) {
      return;
    }

    const imageLink = target.closest<HTMLElement>("[data-icn-id]");
    if (imageLink) {
      const icnId = imageLink.getAttribute("data-icn-id");
      if (icnId) {
        event.preventDefault();
        activateGraphic(icnId);
      }
      return;
    }

    const dmLink = target.closest<HTMLElement>("[data-dm-id]");
    if (dmLink) {
      const dmId = dmLink.getAttribute("data-dm-id");
      if (dmId) {
        event.preventDefault();
        setFilters(defaultFilters);
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
    } catch (err) {
      setError(String(err));
    }
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

  return (
    <div className="app-shell">
      <header className="top-banner">
        <div>
          <h1>S1000D Viewer</h1>
          <p>{userNameDisplay} ({roles.join(", ")})</p>
        </div>
        <button onClick={logout}>Logout</button>
      </header>

      {error ? <div className="error floating">{error}</div> : null}

      <section className="workspace">
        <aside className="panel left">
          <div className="panel-title">Modules</div>

          <div className="filters">
            <label>
              Aircraft
              <select
                value={filters.aircraft}
                onChange={(event) => setFilters((prev) => ({ ...prev, aircraft: event.target.value }))}
              >
                <option value="">All</option>
                {aircraftOptions.map((value) => (
                  <option key={value} value={value}>{value}</option>
                ))}
              </select>
            </label>
            <label>
              Engine
              <select
                value={filters.engine}
                onChange={(event) => setFilters((prev) => ({ ...prev, engine: event.target.value }))}
              >
                <option value="">All</option>
                {engineOptions.map((value) => (
                  <option key={value} value={value}>{value}</option>
                ))}
              </select>
            </label>
          </div>

          <div className="module-list">
            {modules.map((module) => (
              <button
                key={module.dmId}
                className={module.dmId === selectedDmId ? "module-item active" : "module-item"}
                onClick={() => setSelectedDmId(module.dmId)}
              >
                <strong>{module.dmId}</strong>
                <span>{module.title}</span>
                <small>{formatApplicability(module.applicability.aircraft)} / {formatApplicability(module.applicability.engine)}</small>
              </button>
            ))}
          </div>

          {canUpload ? (
            <div className="upload-zone">
              <button type="button" className="upload-toggle" onClick={() => setShowUpload((open) => !open)}>
                {showUpload ? "Hide Upload Module" : "Upload Module"}
              </button>
              {showUpload ? (
                <form className="upload" onSubmit={handleUpload}>
                  <input type="file" accept=".xml" onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)} />
                  <input placeholder="Title" value={uploadTitle} onChange={(e) => setUploadTitle(e.target.value)} />
                  <input placeholder="Aircraft" value={uploadAircraft} onChange={(e) => setUploadAircraft(e.target.value)} />
                  <input placeholder="Engine" value={uploadEngine} onChange={(e) => setUploadEngine(e.target.value)} />
                  <input placeholder="ICN ID" value={uploadIcnId} onChange={(e) => setUploadIcnId(e.target.value)} />
                  <button type="submit">Upload</button>
                </form>
              ) : null}
            </div>
          ) : null}

          {isAdmin ? (
            <div className="admin-tools">
              <button onClick={loadUsers}>Load Users</button>
              {users.map((u) => (
                <div key={u.username} className="user-row">
                  <strong>{u.username}</strong>
                  <span>{u.roles.join(", ")}</span>
                </div>
              ))}
            </div>
          ) : null}
        </aside>

        <main className="panel center">
          <div className="panel-title">Preview</div>
          <input
            className="search"
            placeholder="Search and highlight text"
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
          />
          {selectedContent ? (
            <div className="preview-meta">
              Source: {selectedContent.source} | Applicability: {selectedContent.meta.applicabilityResult}
            </div>
          ) : null}
          <div className="preview dm-rendered" onClick={handlePreviewClick} dangerouslySetInnerHTML={{ __html: highlighted }} />
        </main>

        <aside className="panel right" ref={rightPanelRef}>
          <div className="panel-title">Graphics & Hotspots</div>
          <div className="graphic-meta">{activeIcnId ? `Selected image: ${activeIcnId}` : "No image selected."}</div>
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
                    setFilters(defaultFilters);
                    setSelectedDmId(resolveModuleDmId(hotspot.targetDmId));
                  }
                }}
              >
                <span>{hotspot.label}</span>
              </button>
            ))}
          </div>
        </aside>
      </section>
    </div>
  );
}
