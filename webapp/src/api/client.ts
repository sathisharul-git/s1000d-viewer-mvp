import type { Hotspot, LoginResponse, ModuleContent, ModuleSummary, UploadResponse, UserSummary } from "../types/models";

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

  modules: (token: string, filters: { aircraft: string; engine: string }) =>
    request<ModuleSummary[]>(`/api/modules${buildQuery(filters)}`, { method: "GET" }, token),

  moduleContent: (token: string, dmId: string, filters: { aircraft: string; engine: string }) =>
    request<ModuleContent>(`/api/modules/${encodeURIComponent(dmId)}${buildQuery(filters)}`, { method: "GET" }, token),

  upload: (
    token: string,
    payload: { file: File; aircraft: string; engine: string; title: string; icnId: string },
  ) => {
    const form = new FormData();
    form.append("file", payload.file);
    form.append("aircraft", payload.aircraft);
    form.append("engine", payload.engine);
    form.append("title", payload.title);
    form.append("icnId", payload.icnId);
    return request<UploadResponse>("/api/modules/upload", { method: "POST", body: form }, token);
  },

  graphic: (token: string, icnId: string) => request<string>(`/api/graphics/${encodeURIComponent(icnId)}`, { method: "GET" }, token),

  hotspots: (token: string, icnId: string) =>
    request<Hotspot[]>(`/api/graphics/${encodeURIComponent(icnId)}/hotspots`, { method: "GET" }, token),

  users: (token: string) => request<UserSummary[]>("/api/admin/users", { method: "GET" }, token),
};
