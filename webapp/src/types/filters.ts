export type ApplicabilityFilters = {
  aircraft: string;
  engine: string;
  variant: string;
};

export const defaultApplicabilityFilters: ApplicabilityFilters = {
  aircraft: "",
  engine: "",
  variant: "",
};
