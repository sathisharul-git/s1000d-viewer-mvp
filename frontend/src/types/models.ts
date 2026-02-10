export type LoginResponse = {
  token: string;
  username: string;
  roles: string[];
};

export type ModuleSummary = {
  dmId: string;
  title: string;
  aircraft: string;
  engine: string;
  icnId: string;
  fileName: string;
};

export type ModuleContent = {
  metadata: ModuleSummary;
  htmlContent: string;
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