// Vista "brand" pubblica (topbar): la ritorna GET /api/workspace, leggibile da ogni autenticato.
export interface IWorkspace {
    name: string;
    brandColor: string;
    logoUrl: string | null;
    calcomUrl: string | null;
}

export interface IWorkspaceProfile {
    name: string;
    brandColor: string;
    logoUrl: string | null;
    calcomUrl: string | null;
}
