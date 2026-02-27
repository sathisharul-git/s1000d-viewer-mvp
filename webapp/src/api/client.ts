import type {
  Hotspot,
  LoginResponse,
  ModuleListResponse,
  ModuleRenderResponse,
  PmcListItem,
  PublicationModulesResponse,
  UploadResponse,
  UserSummary,
  ZipImportResponse,
} from "../types/models";

const TOKEN_KEY = "s1000d.jwt";

function buildQuery(params: Record<string, string>) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value.trim()) {
      searchParams.set(key, value.trim());
    }
  });
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

function withProductAttributes(
  base: Record<string, string>,
  productAttributes?: Record<string, string>,
): Record<string, string> {
  const merged: Record<string, string> = { ...base };
  if (!productAttributes) {
    return merged;
  }
  Object.entries(productAttributes).forEach(([key, value]) => {
    const trimmedKey = key.trim();
    const trimmedValue = value.trim();
    if (!trimmedKey || !trimmedValue) {
      return;
    }
    merged[`pa.${trimmedKey}`] = trimmedValue;
  });
  return merged;
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, { ...options, headers });
  if (!response.ok) {
    const raw = await response.text();
    throw new Error(raw || `Request failed with ${response.status}`);
  }

  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  return (await response.text()) as T;
}

export const authStorage = {
  readToken: () => localStorage.getItem(TOKEN_KEY) || "",
  writeToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  me: (token: string) => request<LoginResponse>("/api/auth/me", { method: "GET" }, token),

  modules: (token: string, filters: { aircraft: string; engine: string; variant: string }) =>
    request<ModuleListResponse>(`/api/modules${buildQuery(filters)}`, { method: "GET" }, token),

  moduleRender: (
    token: string,
    dmId: string,
    filters: { aircraft: string; engine: string; variant: string },
    options?: { pmcId?: string; productAttributes?: Record<string, string> },
  ) =>
    request<ModuleRenderResponse>(
      `/api/modules/${encodeURIComponent(dmId)}/render${buildQuery(
        withProductAttributes(
          {
            ...filters,
            pmcId: options?.pmcId?.trim() ?? "",
          },
          options?.productAttributes,
        ),
      )}`,
      { method: "GET" },
      token,
    ),

  pmcs: (token: string) => request<PmcListItem[]>("/api/pmc", { method: "GET" }, token),

  publicationModules: (
    token: string,
    pmcId: string,
    filters: { aircraft: string; engine: string; variant: string },
    productAttributes?: Record<string, string>,
  ) =>
    request<PublicationModulesResponse>(
      `/api/publications/${encodeURIComponent(pmcId)}/modules${buildQuery(withProductAttributes(filters, productAttributes))}`,
      { method: "GET" },
      token,
    ),

  upload: (
    token: string,
    payload: { file: File; aircraft: string; engine: string; variant: string; title: string; icnId: string },
  ) => {
    const form = new FormData();
    form.append("file", payload.file);
    form.append("aircraft", payload.aircraft);
    form.append("engine", payload.engine);
    form.append("variant", payload.variant);
    form.append("title", payload.title);
    form.append("icnId", payload.icnId);
    return request<UploadResponse>("/api/modules/upload", { method: "POST", body: form }, token);
  },

  graphic: (token: string, icnId: string) => request<string>(`/api/graphics/${encodeURIComponent(icnId)}`, { method: "GET" }, token),

  hotspots: (token: string, icnId: string) =>
    request<Hotspot[]>(`/api/graphics/${encodeURIComponent(icnId)}/hotspots`, { method: "GET" }, token),

  users: (token: string) => request<UserSummary[]>("/api/admin/users", { method: "GET" }, token),
  reindex: (token: string) => request<string>("/api/admin/reindex", { method: "POST" }, token),
  importZip: (token: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<ZipImportResponse>("/api/modules/import-zip", { method: "POST", body: form }, token);
  },
};
