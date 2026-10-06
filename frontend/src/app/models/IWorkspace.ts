// Vista "brand" pubblica (topbar): la ritorna GET /api/workspace, leggibile da ogni autenticato.
export interface IWorkspace {
  name: string;
  brandColor: string;
  logoUrl: string | null;
  calcomUrl: string | null;
}

// Vista admin (GET /api/workspace/profile): brand + anagrafica fiscale che finisce nell'intestazione dei PDF
// e nel CedentePrestatore della fattura elettronica
export interface IWorkspaceProfile {
  name: string;
  brandColor: string;
  logoUrl: string | null;
  calcomUrl: string | null;
  legalName: string | null;
  vatNumber: string | null;
  taxCode: string | null;
  address: string | null;
  contactLine: string | null;
  fiscalRegime: string | null;
  street: string | null;
  zipCode: string | null;
  city: string | null;
  province: string | null;
  countryCode: string | null;
}

// payload della PATCH /api/workspace
export interface IWorkspaceUpdate {
  name: string;
  brandColor: string;
  logoUrl: string | null;
  calcomUrl?: string;
  legalName?: string;
  vatNumber?: string;
  taxCode?: string;
  address?: string;
  contactLine?: string;
  fiscalRegime?: string;
  street?: string;
  zipCode?: string;
  city?: string;
  province?: string;
  countryCode?: string;
}
