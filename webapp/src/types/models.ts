export type LoginResponse = {
  token: string;
  username: string;
  roles: string[];
};

export type Applicability = {
  aircraft: string[];
  engine: string[];
  variant: string[];
};

export type ModuleListItem = {
  dmId: string;
  title: string;
  applicability: Applicability;
  source: "published" | "csdb";
  hasPublishedPreview: boolean;
  applicabilityResult: "APPLICABLE" | "NOT_APPLICABLE" | "UNKNOWN";
  applicabilityReason: string;
  applicabilitySource: "published" | "meta" | "metadata" | "dmHeader" | "none";
};

export type ModuleListResponse = {
  filters: {
    aircraft: string | null;
    engine: string | null;
    variant: string | null;
  };
  modules: ModuleListItem[];
};

export type ModuleRenderResponse = {
  dmId: string;
  source: "published" | "quick";
  html: string;
  applicability?: {
    dmStatus: "APPLICABLE" | "NOT_APPLICABLE" | "UNKNOWN";
    displayText: string;
    reason: string;
    source: "metadata" | "dmHeader" | "none" | string;
  };
  inlineApplicability?: {
    mode: "FILTERED" | "MARKED" | "NONE" | string;
    removedCount: number;
    keptCount: number;
  };
  meta: {
    title: string;
    applicability: Applicability;
    applicabilityResult: "APPLICABLE" | "NOT_APPLICABLE" | "UNKNOWN";
    applicabilityReason: string;
    applicabilitySource: "published" | "meta" | "metadata" | "dmHeader" | "none";
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

export type ZipImportResponse = {
  importedDmCount: number;
  importedPmcCount: number;
  importedIcnCount: number;
  skippedCount: number;
  message: string;
};

export type PmcListItem = {
  pmcId: string;
  title: string;
};

export type PublicationModuleItem = {
  dmId: string;
  displayName: string;
  systemCode: string;
  infoCode: string;
  dmApplicabilityStatus: "APPLICABLE" | "NOT_APPLICABLE" | "UNKNOWN";
  dmApplicabilityDisplayText: string;
  dmApplicabilityReason: string;
  dmApplicabilitySource: "metadata" | "dmHeader" | "none" | string;
  applicability: Applicability;
  source: "published" | "csdb";
  hasPublishedPreview: boolean;
};

export type PublicationModulesResponse = {
  pmcId: string;
  title: string;
  filters: {
    aircraft: string | null;
    engine: string | null;
    variant: string | null;
  };
  modules: PublicationModuleItem[];
};
