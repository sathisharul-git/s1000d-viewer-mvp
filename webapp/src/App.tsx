import { FormEvent, MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import { api, authStorage } from "./api/client";
import { BreadcrumbBar } from "./components/breadcrumb/BreadcrumbBar";
import { ThreePaneLayout, type PaneWidths } from "./components/layout/ThreePaneLayout";
import { type ApplicabilityFilters, defaultApplicabilityFilters } from "./types/filters";
import type {
  Hotspot,
  ModuleListItem,
  ModuleRenderResponse,
  PmcListItem,
  PublicationModuleItem,
  UserSummary,
} from "./types/models";

type ViewerLayoutState = {
  leftWidth: number;
  rightWidth: number;
  rightOpen: boolean;
};

type HotspotsByIcn = Record<string, Hotspot[]>;
type HotspotStatusByIcn = Record<string, string>;

const layoutStorageKey = "s1000d.viewer.layout.v3";
const defaultLayoutState: ViewerLayoutState = {
  leftWidth: 320,
  rightWidth: 360,
  rightOpen: false,
};

function hasRole(roles: string[], role: string): boolean {
  const targets = new Set<string>([role]);
  if (role === "ROLE_ADMIN") {
    targets.add("ROLE_S1000D-ADMIN");
  } else if (role === "ROLE_ENGINEER") {
    targets.add("ROLE_S1000D-ENGINEER");
  } else if (role === "ROLE_VIEWER") {
    targets.add("ROLE_S1000D-VIEWER");
  }
  return roles.some((candidate) => targets.has(candidate));
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

function normalizeHotspotId(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) {
    return "";
  }
  return trimmed.startsWith("#") ? trimmed.slice(1).trim() : trimmed;
}

function hotspotRowKey(icnId: string, hotspotId: string): string {
  return `${icnId}::${normalizeHotspotId(hotspotId)}`;
}

function resolveKnownHotspotId(rawId: string, hotspots: Hotspot[]): string {
  const normalized = normalizeHotspotId(rawId);
  if (!normalized) {
    return "";
  }
  const exact = hotspots.find((item) => normalizeHotspotId(item.id) === normalized);
  if (exact) {
    return exact.id;
  }
  const base = normalized.split("-")[0];
  const baseMatch = hotspots.find((item) => normalizeHotspotId(item.id) === base);
  return baseMatch?.id ?? normalized;
}

function detectHotspotIdFromSvgTarget(target: HTMLElement, knownHotspots: Hotspot[]): string {
  const hotspotNode = target.closest<HTMLElement>("[data-hotspot-id]");
  if (hotspotNode) {
    return resolveKnownHotspotId(hotspotNode.getAttribute("data-hotspot-id") ?? "", knownHotspots);
  }

  const idNode = target.closest<HTMLElement>("[id]");
  if (!idNode) {
    return "";
  }
  return resolveKnownHotspotId(idNode.getAttribute("id") ?? "", knownHotspots);
}

function publicationToModuleListItem(item: PublicationModuleItem): ModuleListItem {
  const source =
    item.dmApplicabilitySource === "published" ||
    item.dmApplicabilitySource === "meta" ||
    item.dmApplicabilitySource === "metadata" ||
    item.dmApplicabilitySource === "dmHeader" ||
    item.dmApplicabilitySource === "none"
      ? item.dmApplicabilitySource
      : "none";
  return {
    dmId: item.dmId,
    title: item.displayName || item.dmId,
    applicability: item.applicability ?? { aircraft: [], engine: [], variant: [] },
    source: item.source,
    hasPublishedPreview: item.hasPublishedPreview,
    applicabilityResult: item.dmApplicabilityStatus,
    applicabilityReason: item.dmApplicabilityReason || "no constraints requested",
    applicabilitySource: source,
  };
}

export function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState(authStorage.readToken());
  const [userNameDisplay, setUserNameDisplay] = useState("");
  const [roles, setRoles] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [errorFading, setErrorFading] = useState(false);
  const [noticeFading, setNoticeFading] = useState(false);
  const [busy, setBusy] = useState(false);

  const [catalog, setCatalog] = useState<ModuleListItem[]>([]);
  const [modules, setModules] = useState<ModuleListItem[]>([]);
  const [pmcs, setPmcs] = useState<PmcListItem[]>([]);
  const [selectedPmcId, setSelectedPmcId] = useState("");
  const [filters, setFilters] = useState<ApplicabilityFilters>(defaultApplicabilityFilters);
  const [moduleSearch, setModuleSearch] = useState("");

  const [selectedDmId, setSelectedDmId] = useState("");
  const [selectedContent, setSelectedContent] = useState<ModuleRenderResponse | null>(null);
  const [selectedIcnId, setSelectedIcnId] = useState("");
  const [selectedHotspotId, setSelectedHotspotId] = useState("");

  const [graphicSvg, setGraphicSvg] = useState("");
  const [hotspotsByIcn, setHotspotsByIcn] = useState<HotspotsByIcn>({});
  const [loadingHotspotsByIcn, setLoadingHotspotsByIcn] = useState<HotspotStatusByIcn>({});
  const [hotspotErrorsByIcn, setHotspotErrorsByIcn] = useState<HotspotStatusByIcn>({});

  const [searchTerm, setSearchTerm] = useState("");
  const [layout, setLayout] = useState<ViewerLayoutState>(() => readLayoutState());

  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadAircraft, setUploadAircraft] = useState("");
  const [uploadEngine, setUploadEngine] = useState("");
  const [uploadVariant, setUploadVariant] = useState("");
  const [uploadIcnId, setUploadIcnId] = useState("");
  const [showUpload, setShowUpload] = useState(false);
  const [zipFile, setZipFile] = useState<File | null>(null);
  const [zipBusy, setZipBusy] = useState(false);

  const [users, setUsers] = useState<UserSummary[]>([]);
  const [showUsers, setShowUsers] = useState(false);
  const [reindexBusy, setReindexBusy] = useState(false);
  const hotspotRowRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const graphicSvgRef = useRef<HTMLDivElement | null>(null);
  const uploadFileInputRef = useRef<HTMLInputElement | null>(null);
  const zipFileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    writeLayoutState(layout);
  }, [layout]);

  useEffect(() => {
    if (!notice) {
      setNoticeFading(false);
      return;
    }

    setNoticeFading(false);
    const fadeTimer = window.setTimeout(() => {
      setNoticeFading(true);
    }, 4800);
    const clearTimer = window.setTimeout(() => {
      setNotice("");
      setNoticeFading(false);
    }, 6000);

    return () => {
      window.clearTimeout(fadeTimer);
      window.clearTimeout(clearTimer);
    };
  }, [notice]);

  useEffect(() => {
    if (!error) {
      setErrorFading(false);
      return;
    }

    setErrorFading(false);
    const fadeTimer = window.setTimeout(() => {
      setErrorFading(true);
    }, 4800);
    const clearTimer = window.setTimeout(() => {
      setError("");
      setErrorFading(false);
    }, 6000);

    return () => {
      window.clearTimeout(fadeTimer);
      window.clearTimeout(clearTimer);
    };
  }, [error]);

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

  async function loadModulesForScope(currentToken: string, nextFilters: ApplicabilityFilters): Promise<ModuleListItem[]> {
    if (selectedPmcId) {
      const response = await api.publicationModules(currentToken, selectedPmcId, nextFilters);
      return response.modules.map(publicationToModuleListItem);
    }
    const response = await api.modules(currentToken, nextFilters);
    return response.modules;
  }

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
      setPmcs([]);
      setSelectedPmcId("");
      return;
    }
    api.pmcs(token)
      .then((rows) => {
        setPmcs(rows);
        setSelectedPmcId((current) => {
          if (current && rows.some((row) => row.pmcId === current)) {
            return current;
          }
          return rows[0]?.pmcId ?? "";
        });
      })
      .catch((err: unknown) => setError(String(err)));
  }, [token]);

  useEffect(() => {
    if (!token) {
      return;
    }
    loadModulesForScope(token, defaultApplicabilityFilters)
      .then((rows) => setCatalog(rows))
      .catch((err: unknown) => setError(String(err)));
  }, [selectedPmcId, token]);

  useEffect(() => {
    setSelectedDmId("");
    setSelectedContent(null);
    setSelectedIcnId("");
    setSelectedHotspotId("");
    setGraphicSvg("");
    setHotspotsByIcn({});
    setLoadingHotspotsByIcn({});
    setHotspotErrorsByIcn({});
  }, [selectedPmcId]);

  useEffect(() => {
    if (!token) {
      return;
    }
    loadModulesForScope(token, filters)
      .then((rows) => {
        setModules(rows);
        if (!rows.find((row) => row.dmId === selectedDmId)) {
          const nextDmId = rows[0]?.dmId ?? "";
          setSelectedDmId(nextDmId);
        }
      })
      .catch((err: unknown) => setError(String(err)));
  }, [filters, selectedPmcId, token]);

  useEffect(() => {
    if (!token || !selectedDmId) {
      setSelectedContent(null);
      setSelectedIcnId("");
      setSelectedHotspotId("");
      setGraphicSvg("");
      setHotspotsByIcn({});
      setLoadingHotspotsByIcn({});
      setHotspotErrorsByIcn({});
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const content = await api.moduleRender(token, selectedDmId, filters, {
          pmcId: selectedPmcId || undefined,
        });
        if (cancelled) {
          return;
        }
        setSelectedContent(content);
        setSelectedIcnId("");
        setSelectedHotspotId("");
        setGraphicSvg("");
        setHotspotsByIcn({});
        setLoadingHotspotsByIcn({});
        setHotspotErrorsByIcn({});
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(String(err));
        setSelectedContent(null);
        setSelectedIcnId("");
        setSelectedHotspotId("");
        setGraphicSvg("");
        setHotspotsByIcn({});
        setLoadingHotspotsByIcn({});
        setHotspotErrorsByIcn({});
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [filters, selectedDmId, selectedPmcId, token]);

  useEffect(() => {
    const icnIds = selectedContent?.assets.icns ?? [];
    if (!token || icnIds.length === 0) {
      setHotspotsByIcn({});
      setLoadingHotspotsByIcn({});
      setHotspotErrorsByIcn({});
      return;
    }

    const loadingMap: HotspotStatusByIcn = {};
    icnIds.forEach((icnId) => {
      loadingMap[icnId] = "loading";
    });
    setLoadingHotspotsByIcn(loadingMap);
    setHotspotErrorsByIcn({});

    let cancelled = false;
    (async () => {
      const resolvedMap: HotspotsByIcn = {};
      const errors: HotspotStatusByIcn = {};
      await Promise.all(
        icnIds.map(async (icnId) => {
          try {
            resolvedMap[icnId] = await api.hotspots(token, icnId);
          } catch (err) {
            resolvedMap[icnId] = [];
            errors[icnId] = String(err);
          }
        }),
      );

      if (cancelled) {
        return;
      }

      const completeLoadingMap: HotspotStatusByIcn = {};
      icnIds.forEach((icnId) => {
        completeLoadingMap[icnId] = "";
      });
      setLoadingHotspotsByIcn(completeLoadingMap);
      setHotspotErrorsByIcn(errors);
      setHotspotsByIcn(resolvedMap);
    })();

    return () => {
      cancelled = true;
    };
  }, [selectedContent, token]);

  useEffect(() => {
    if (!token || !selectedIcnId) {
      setGraphicSvg("");
      return;
    }

    let cancelled = false;
    (async () => {
      try {
        const svg = await api.graphic(token, selectedIcnId);
        if (cancelled) {
          return;
        }
        setGraphicSvg(svg);
      } catch (err) {
        if (cancelled) {
          return;
        }
        setError(String(err));
        setGraphicSvg("");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [selectedIcnId, token]);

  useEffect(() => {
    if (!selectedIcnId || !selectedHotspotId) {
      return;
    }
    const key = hotspotRowKey(selectedIcnId, selectedHotspotId);
    const node = hotspotRowRefs.current[key];
    node?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [selectedHotspotId, selectedIcnId, hotspotsByIcn]);

  useEffect(() => {
    const host = graphicSvgRef.current;
    if (!host) {
      return;
    }

    const svg = host.querySelector<SVGSVGElement>("svg");
    if (!svg) {
      return;
    }

    svg.querySelectorAll(".hotspot-highlighted").forEach((node) => {
      node.classList.remove("hotspot-highlighted");
    });
    svg.querySelectorAll(".hotspot-callout-highlighted").forEach((node) => {
      node.classList.remove("hotspot-callout-highlighted");
    });

    const targetHotspotId = normalizeHotspotId(selectedHotspotId);
    if (!targetHotspotId) {
      return;
    }

    const hotspots = Array.from(svg.querySelectorAll<HTMLElement>("[data-hotspot-id]"));
    const matchedNodes = hotspots.filter((node) => {
      const candidate = normalizeHotspotId(node.getAttribute("data-hotspot-id") ?? "");
      if (!candidate) {
        return false;
      }
      if (candidate === targetHotspotId) {
        return true;
      }
      const candidateBase = candidate.split("-")[0];
      const targetBase = targetHotspotId.split("-")[0];
      return candidateBase === targetBase;
    });
    if (!matchedNodes.length) {
      return;
    }

    for (const matchedNode of matchedNodes) {
      const hotspotGroup = matchedNode.closest<SVGGElement>(".s1000d-hotspot") ?? (matchedNode as unknown as SVGGElement);
      hotspotGroup.classList.add("hotspot-highlighted");
      const shape = hotspotGroup.querySelector<SVGGraphicsElement>(".s1000d-hotspot-shape");
      if (shape) {
        shape.classList.add("hotspot-highlighted");
      }
    }

    const targetBase = targetHotspotId.split("-")[0];
    const textMarkers = Array.from(svg.querySelectorAll<SVGTextElement>("text, tspan"));
    for (const marker of textMarkers) {
      const token = normalizeHotspotId(marker.textContent ?? "").replace(/[^\w.-]+/g, "");
      if (token === targetBase) {
        marker.classList.add("hotspot-callout-highlighted");
      }
    }

    const firstHotspotGroup =
      matchedNodes[0]?.closest<SVGGElement>(".s1000d-hotspot") ?? (matchedNodes[0] as unknown as SVGGElement | undefined);
    firstHotspotGroup?.scrollIntoView({ block: "center", inline: "center", behavior: "smooth" });
  }, [graphicSvg, selectedHotspotId, selectedIcnId]);

  const highlighted = useMemo(
    () => highlightHtml(selectedContent?.html ?? "<p>Select a data module to preview content.</p>", searchTerm),
    [searchTerm, selectedContent],
  );

  const selectedGraphicHotspots = useMemo(() => {
    if (!selectedIcnId) {
      return [];
    }
    return hotspotsByIcn[selectedIcnId] ?? [];
  }, [hotspotsByIcn, selectedIcnId]);

  function logout() {
    setToken("");
    setRoles([]);
    setUserNameDisplay("");
    setSelectedContent(null);
    setGraphicSvg("");
    setHotspotsByIcn({});
    setLoadingHotspotsByIcn({});
    setHotspotErrorsByIcn({});
    setSelectedIcnId("");
    setSelectedHotspotId("");
    setSelectedDmId("");
    setPmcs([]);
    setSelectedPmcId("");
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
    setNotice("");
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
    setNotice("");
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
        loadModulesForScope(token, defaultApplicabilityFilters),
        loadModulesForScope(token, filters),
      ]);
      setCatalog(catalogRows);
      setModules(filteredRows);

      setUploadFile(null);
      setUploadTitle("");
      setUploadAircraft("");
      setUploadEngine("");
      setUploadVariant("");
      setUploadIcnId("");
      setShowUpload(false);
      setNotice("Module uploaded and indexed successfully.");
      if (uploadFileInputRef.current) {
        uploadFileInputRef.current.value = "";
      }
    } catch (err) {
      setError(String(err));
    }
  }

  function openGraphic(icnId: string, hotspotId?: string) {
    const normalized = icnId.trim();
    if (!normalized) {
      return;
    }
    setSelectedIcnId(normalized);
    if (hotspotId) {
      setSelectedHotspotId(normalizeHotspotId(hotspotId));
    } else {
      setSelectedHotspotId("");
    }
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

  function selectHotspot(icnId: string, hotspotId: string) {
    const nextHotspotId = normalizeHotspotId(hotspotId);
    if (!nextHotspotId) {
      return;
    }
    openGraphic(icnId, nextHotspotId);
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

  function handleSvgClick(event: MouseEvent<HTMLDivElement>) {
    const target = event.target as HTMLElement | null;
    if (!target) {
      return;
    }
    const hotspotId = detectHotspotIdFromSvgTarget(target, selectedGraphicHotspots);
    if (!hotspotId) {
      return;
    }
    setSelectedHotspotId(normalizeHotspotId(hotspotId));
  }

  async function loadUsers() {
    if (!token) {
      return;
    }
    setError("");
    try {
      setUsers(await api.users(token));
      setShowUsers(true);
    } catch (err) {
      setError(String(err));
    }
  }

  async function handleZipImport(event: FormEvent) {
    event.preventDefault();
    if (!token || !zipFile) {
      setError("Select a ZIP file to import.");
      return;
    }
    setError("");
    setNotice("");
    setZipBusy(true);
    try {
      const result = await api.importZip(token, zipFile);
      const [catalogRows, filteredRows] = await Promise.all([
        loadModulesForScope(token, defaultApplicabilityFilters),
        loadModulesForScope(token, filters),
      ]);
      setCatalog(catalogRows);
      setModules(filteredRows);
      if (!filteredRows.find((row) => row.dmId === selectedDmId)) {
        setSelectedDmId(filteredRows[0]?.dmId ?? "");
      }
      setZipFile(null);
      setUploadFile(null);
      setUploadTitle("");
      setUploadAircraft("");
      setUploadEngine("");
      setUploadVariant("");
      setUploadIcnId("");
      if (zipFileInputRef.current) {
        zipFileInputRef.current.value = "";
      }
      if (uploadFileInputRef.current) {
        uploadFileInputRef.current.value = "";
      }
      setShowUpload(false);
      setNotice(result.message);
    } catch (err) {
      setError(String(err));
    } finally {
      setZipBusy(false);
    }
  }

  async function handleReindex() {
    if (!token) {
      return;
    }
    setError("");
    setNotice("");
    setReindexBusy(true);
    try {
      await api.reindex(token);
      const [catalogRows, filteredRows] = await Promise.all([
        loadModulesForScope(token, defaultApplicabilityFilters),
        loadModulesForScope(token, filters),
      ]);
      setCatalog(catalogRows);
      setModules(filteredRows);

      if (!filteredRows.find((row) => row.dmId === selectedDmId)) {
        setSelectedDmId(filteredRows[0]?.dmId ?? "");
      } else if (selectedDmId) {
        const refreshed = await api.moduleRender(token, selectedDmId, filters, {
          pmcId: selectedPmcId || undefined,
        });
        setSelectedContent(refreshed);
      }
      setNotice("Reindex completed successfully.");
    } catch (err) {
      setError(String(err));
    } finally {
      setReindexBusy(false);
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
  const selectedHotspotKey = normalizeHotspotId(selectedHotspotId);

  return (
    <div className="app-shell">
      <header className="top-banner">
        <div>
          <h1>S1000D Viewer</h1>
          <p>{userNameDisplay} ({roles.join(", ")})</p>
        </div>
        <div className="top-actions">
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
        pmcs={pmcs}
        selectedPmcId={selectedPmcId}
        onSelectPmc={setSelectedPmcId}
        selectedDmLabel={selectedDmLabel}
        selectedGraphicId={selectedIcnId}
        filters={filters}
        aircraftOptions={aircraftOptions}
        engineOptions={engineOptions}
        variantOptions={variantOptions}
        onApplyFilters={setFilters}
        canUpload={canUpload}
        showUpload={showUpload}
        onToggleUpload={() => setShowUpload((open) => !open)}
        canReindex={isAdmin}
        reindexBusy={reindexBusy}
        onReindex={handleReindex}
      />

      {error ? <div className={errorFading ? "error floating fading" : "error floating"}>{error}</div> : null}
      {notice ? <div className={noticeFading ? "notice floating fading" : "notice floating"}>{notice}</div> : null}

      {showUpload && canUpload ? (
        <section className="utility-panel">
          <div className="utility-section">
            <div className="utility-title">Upload Data Module XML</div>
            <form className="upload-grid" onSubmit={handleUpload}>
              <input ref={uploadFileInputRef} type="file" accept=".xml" onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)} />
              <input placeholder="Title" value={uploadTitle} onChange={(e) => setUploadTitle(e.target.value)} />
              <input placeholder="Aircraft" value={uploadAircraft} onChange={(e) => setUploadAircraft(e.target.value)} />
              <input placeholder="Engine" value={uploadEngine} onChange={(e) => setUploadEngine(e.target.value)} />
              <input placeholder="Variant" value={uploadVariant} onChange={(e) => setUploadVariant(e.target.value)} />
              <input placeholder="ICN ID" value={uploadIcnId} onChange={(e) => setUploadIcnId(e.target.value)} />
              <button type="submit">Upload</button>
            </form>
          </div>
          <div className="utility-divider" />
          <div className="utility-section">
            <div className="utility-title">Import Dataset ZIP</div>
            <form className="zip-import-form" onSubmit={handleZipImport}>
              <input
                ref={zipFileInputRef}
                type="file"
                accept=".zip,application/zip"
                onChange={(event) => setZipFile(event.target.files?.[0] ?? null)}
              />
              <button type="submit" disabled={zipBusy}>
                {zipBusy ? "Importing..." : "Import ZIP Dataset"}
              </button>
            </form>
            <div className="utility-help">Accepted: DMC-*.xml, PMC-*.xml, ICN-*.(cgm|svg|png|jpg|jpeg|gif)</div>
          </div>
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
                {selectedContent.applicability?.displayText ? (
                  <span className="meta-note" title="DM-level applicability text">
                    {selectedContent.applicability.displayText}
                  </span>
                ) : null}
                {selectedContent.inlineApplicability && selectedContent.inlineApplicability.mode !== "NONE" ? (
                  <span className="meta-note" title="Inline applicability filtering applied">
                    Hidden: {selectedContent.inlineApplicability.removedCount}, Visible: {selectedContent.inlineApplicability.keptCount}
                  </span>
                ) : null}
              </div>
            ) : null}

            {selectedContent?.assets.icns.length ? (
              <div className="preview-assets-grid">
                {selectedContent.assets.icns.map((icnId) => (
                  <section key={icnId} className={icnId === selectedIcnId ? "asset-card active" : "asset-card"}>
                    <button type="button" className={icnId === selectedIcnId ? "asset-btn active" : "asset-btn"} onClick={() => openGraphic(icnId)}>
                      Open Graphic: {icnId}
                    </button>
                    <details className="asset-hotspots" open={icnId === selectedIcnId}>
                      <summary>Hotspots</summary>
                      <div className="hotspot-link-list">
                        {loadingHotspotsByIcn[icnId] === "loading" ? <div className="hotspot-empty">Loading hotspots...</div> : null}
                        {loadingHotspotsByIcn[icnId] !== "loading" && hotspotErrorsByIcn[icnId] ? (
                          <div className="hotspot-empty">Unable to load hotspots.</div>
                        ) : null}
                        {loadingHotspotsByIcn[icnId] !== "loading" &&
                        !hotspotErrorsByIcn[icnId] &&
                        (hotspotsByIcn[icnId]?.length ?? 0) === 0 ? (
                          <div className="hotspot-empty">No hotspots available.</div>
                        ) : null}
                        {(hotspotsByIcn[icnId] ?? []).map((hotspot) => {
                          const rowId = hotspotRowKey(icnId, hotspot.id);
                          const isSelected = icnId === selectedIcnId && normalizeHotspotId(hotspot.id) === selectedHotspotKey;
                          return (
                            <button
                              key={rowId}
                              ref={(node) => {
                                hotspotRowRefs.current[rowId] = node;
                              }}
                              type="button"
                              className={isSelected ? "hotspot-link hotspot-link-selected" : "hotspot-link"}
                              data-hotspot-id={hotspot.id}
                              data-icn-id={icnId}
                              onClick={() => selectHotspot(icnId, hotspot.id)}
                              title={hotspot.targetDmId ? `${hotspot.label} (target ${hotspot.targetDmId})` : hotspot.label}
                            >
                              {hotspot.label || hotspot.id}
                            </button>
                          );
                        })}
                      </div>
                    </details>
                  </section>
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
                  {selectedIcnId ? `Selected graphic: ${selectedIcnId}` : "Select a graphic from preview to load SVG."}
                </div>
                {!selectedIcnId ? (
                  <div className="graphics-empty">Select a graphic in Preview to enable this panel.</div>
                ) : (
                  <div className="graphic-wrap">
                    {graphicSvg ? (
                      <div
                        ref={graphicSvgRef}
                        className="graphic-svg"
                        onClick={handleSvgClick}
                        dangerouslySetInnerHTML={{ __html: graphicSvg }}
                      />
                    ) : (
                      <p>No image available.</p>
                    )}
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
