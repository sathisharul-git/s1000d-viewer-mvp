export type LoginResponse = {
  token: string;
  username: string;
  roles: string[];
};

export type Applicability = {
  aircraft: string[];
  engine: string[];
};

export type ModuleListItem = {
  dmId: string;
  title: string;
  applicability: Applicability;
  source: "published" | "csdb";
  hasPublishedPreview: boolean;
};

export type ModuleListResponse = {
  filters: {
    aircraft: string | null;
    engine: string | null;
  };
  modules: ModuleListItem[];
};

export type ModuleRenderResponse = {
  dmId: string;
  source: "published" | "quick";
  html: string;
  meta: {
    title: string;
    applicability: Applicability;
    applicabilityResult: "APPLICABLE" | "NOT_APPLICABLE" | "UNKNOWN";
  };
  assets: {
    icns: string[];
  };
  links: {
    dmRefs: string[];
  };
};

export type Hotspot = {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  targetDmId?: string;
};

export type UserSummary = {
  username: string;
  roles: string[];
};

export type UploadResponse = {
  dmId: string;
  message: string;
};
